// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.positions;

import static com.android.tools.r8.utils.positions.PositionUtils.mustHaveResidualDebugInfo;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.debuginfo.DebugRepresentation.DebugRepresentationPredicate;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.MappingComposer;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapSupplierResult;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.OriginalSourceFiles;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.positions.MappedPositionToClassNameMapperBuilder.MappedPositionToClassNamingBuilder;
import com.android.tools.r8.utils.timing.Timing;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class LineNumberOptimizer {

  public static ProguardMapSupplierResult runAndWriteMap(
      AndroidApp inputApp,
      AppView<?> appView,
      ExecutorService executorService,
      Timing timing,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation)
      throws ExecutionException {
    assert appView.options().hasMappingFileSupport();
    if (shouldEmitOriginalMappingFile(appView)) {
      appView.options().reporter.warning(new NotSupportedMapVersionForMappingComposeDiagnostic());
      timing.begin("Spawn write proguard map - emitting original mapping file");
      ProguardMapSupplierResult result =
          ProguardMapSupplier.create(appView.appInfo().app().getProguardMap(), appView.options())
              .writeProguardMap(appView, executorService, timing);
      timing.end();
      return result;
    }
    // When line number optimization is turned off the identity mapping for line numbers is
    // used. We still run the line number optimizer to collect line numbers and inline frame
    // information for the mapping file.
    timing.begin("Line number remapping");
    ClassNameMapper mapper =
        run(appView, inputApp, originalSourceFiles, representation, executorService, timing);
    timing.end();
    if (appView.options().mappingComposeOptions().generatedClassNameMapperConsumer != null) {
      appView.options().mappingComposeOptions().generatedClassNameMapperConsumer.accept(mapper);
    }
    if (appView.options().mappingComposeOptions().enableExperimentalMappingComposition
        && appView.appInfo().app().getProguardMap() != null) {
      try (Timing t0 = timing.begin("Compose proguard map")) {
        mapper =
            ClassNameMapper.mapperFromStringWithPreamble(
                MappingComposer.compose(
                    appView.options(), appView.appInfo().app().getProguardMap(), mapper));
      } catch (IOException | MappingComposeException e) {
        throw new CompilationError(e.getMessage(), e);
      }
    }
    timing.begin("Spawn write proguard map");
    ProguardMapSupplierResult result =
        ProguardMapSupplier.create(mapper, appView.options())
            .writeProguardMap(appView, executorService, timing);
    timing.end();
    return result;
  }

  private static boolean shouldEmitOriginalMappingFile(AppView<?> appView) {
    if (!appView.options().mappingComposeOptions().enableExperimentalMappingComposition
        || appView.appInfo().app().getProguardMap() == null) {
      return false;
    }
    MapVersionMappingInformation mapVersionInfo =
        appView.appInfo().app().getProguardMap().getFirstMapVersionInformation();
    if (mapVersionInfo == null) {
      return true;
    }
    MapVersion newMapVersion = mapVersionInfo.getMapVersion();
    return !ResidualSignatureMappingInformation.isSupported(newMapVersion)
        || newMapVersion.isUnknown();
  }

  public static ClassNameMapper run(
      AppView<?> appView,
      AndroidApp inputApp,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    PositionToMappedRangeMapper positionToMappedRangeMapper =
        PositionToMappedRangeMapper.create(appView);
    MappedPositionToClassNameMapperBuilder builder =
        MappedPositionToClassNameMapperBuilder.builder(appView, originalSourceFiles);

    // Collect which files contain which classes that need to have their line numbers optimized.
    timing.begin("Process classes");
    AppPositionRemapper positionRemapper = AppPositionRemapper.create(appView, inputApp, timing);
    Deque<MappedPositionsForClassResult> worklist = new ConcurrentLinkedDeque<>();
    ThreadUtils.processItemsThatMatches(
        appView.appInfo().classes(),
        clazz -> shouldRun(clazz, appView),
        (clazz, threadTiming) -> {
          MappedPositionsForClassResult classResult =
              runForClass(
                  clazz,
                  appView,
                  representation,
                  positionRemapper,
                  positionToMappedRangeMapper,
                  threadTiming);
          worklist.addLast(classResult);
        },
        appView.options(),
        executorService,
        timing,
        timing.beginMerger("Map positions concurrently", executorService));
    timing.end();

    timing.begin("Add class naming");
    while (!worklist.isEmpty()) {
      MappedPositionsForClassResult classResult = worklist.removeFirst();
      MappedPositionToClassNamingBuilder classNamingBuilder =
          builder.addClassNaming(classResult.getClazz());
      for (MappedPositionsForMethodResult methodResult :
          classResult.getMappedPositionsForMethods()) {
        classNamingBuilder.addMappedPositions(
            methodResult.method,
            methodResult.mappedPositions,
            methodResult.positionRemapper,
            methodResult.canUsePc);
      }
    }
    timing.end();

    // Update all the debug-info objects.
    timing.begin("Update debug info in code objects");
    positionToMappedRangeMapper.updateDebugInfoInCodeObjects();
    timing.end();

    return builder.build();
  }

  private static boolean shouldRun(DexProgramClass clazz, AppView<?> appView) {
    InternalOptions options = appView.options();
    if (options.partialSubCompilationConfiguration == null) {
      return true;
    } else {
      return !options.partialSubCompilationConfiguration.asR8().hasD8DefinitionFor(clazz.getType());
    }
  }

  private static MappedPositionsForClassResult runForClass(
      DexProgramClass clazz,
      AppView<?> appView,
      DebugRepresentationPredicate representation,
      AppPositionRemapper positionRemapper,
      PositionToMappedRangeMapper positionToMappedRangeMapper,
      Timing timing) {
    timing.begin("Prelude");
    IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
        groupMethodsByRenamedName(appView, clazz);

    // Process methods ordered by renamed name.
    List<DexString> renamedMethodNames = new ArrayList<>(methodsByRenamedName.keySet());
    renamedMethodNames.sort(DexString::compareTo);
    timing.end();

    ClassPositionRemapper classPositionRemapper =
        positionRemapper.createClassPositionRemapper(clazz);
    List<MappedPositionsForMethodResult> mappedPositionsForMethodResults = new ArrayList<>();
    for (DexString methodName : renamedMethodNames) {
      List<ProgramMethod> methods = methodsByRenamedName.get(methodName);
      if (methods.size() > 1) {
        // If there are multiple methods with the same name (overloaded) then sort them for
        // deterministic behaviour: the algorithm will assign new line numbers in this order.
        // Methods with different names can share the same line numbers, that's why they don't
        // need to be sorted.
        // If we are compiling to DEX we will try to not generate overloaded names. This saves
        // space by allowing more debug-information to be canonicalized. If we have overloaded
        // methods, we either did not rename them, we renamed them according to a supplied map or
        // they may be bridges for interface methods with covariant return types.
        sortMethods(methods);
        assert verifyMethodsAreKeptDirectlyOrIndirectly(appView, methods);
      }

      timing.begin("Process methods");
      // We must reuse the same MethodPositionRemapper for methods with the same name.
      MethodPositionRemapper methodPositionRemapper =
          classPositionRemapper.createMethodPositionRemapper();
      for (ProgramMethod method : methods) {
        if (shouldRunForMethod(method, appView, methodName, methods)) {
          MappedPositionsForMethodResult mappedPositionsForMethodResult =
              runForMethod(
                  method,
                  appView,
                  methods,
                  methodPositionRemapper,
                  positionToMappedRangeMapper,
                  representation,
                  timing);
          mappedPositionsForMethodResults.add(mappedPositionsForMethodResult);
        }
      }
      timing.end();
    }
    return new MappedPositionsForClassResult(clazz, mappedPositionsForMethodResults);
  }

  private static boolean shouldRunForMethod(
      ProgramMethod method, AppView<?> appView, DexString methodName, List<ProgramMethod> methods) {
    DexEncodedMethod definition = method.getDefinition();
    return !method.getName().isIdenticalTo(methodName)
        || mustHaveResidualDebugInfo(appView.options(), definition)
        || definition.isD8R8Synthesized()
        || methods.size() > 1;
  }

  private static MappedPositionsForMethodResult runForMethod(
      ProgramMethod method,
      AppView<?> appView,
      List<ProgramMethod> methods,
      MethodPositionRemapper positionRemapper,
      PositionToMappedRangeMapper positionToMappedRangeMapper,
      DebugRepresentationPredicate representation,
      Timing timing) {
    Code code = method.getDefinition().getCode();
    if (code == null
        || !(code.isCfCode() || code.isDexCode())
        || appView.isCfByteCodePassThrough(method)) {
      return new MappedPositionsForMethodResult(
          method, Collections.emptyList(), positionRemapper, representation.canUseDexPc(methods));
    }
    try (Timing t0 = timing.begin("Get mapped positions")) {
      int pcEncodingCutoff =
          ObjectUtils.identical(method, methods.get(0))
              ? representation.getDexPcEncodingCutoff(method)
              : -1;
      boolean canUseDexPc = pcEncodingCutoff > 0;
      List<MappedPosition> mappedPositions =
          positionToMappedRangeMapper.getMappedPositions(
              method, positionRemapper, methods.size() > 1, canUseDexPc, pcEncodingCutoff, timing);
      return new MappedPositionsForMethodResult(
          method, mappedPositions, positionRemapper, canUseDexPc);
    }
  }

  @SuppressWarnings("ComplexBooleanConstant")
  private static boolean verifyMethodsAreKeptDirectlyOrIndirectly(
      AppView<?> appView, List<ProgramMethod> methods) {
    if (appView.options().isGeneratingClassFiles() || !appView.appInfo().hasClassHierarchy()) {
      return true;
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfo().withClassHierarchy();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    boolean allSeenAreInstanceInitializers = true;
    DexString originalName;
    for (ProgramMethod method : methods) {
      // We cannot rename instance initializers.
      if (method.getDefinition().isInstanceInitializer()) {
        assert allSeenAreInstanceInitializers;
        continue;
      }
      allSeenAreInstanceInitializers = false;
      // If the method is pinned, we cannot minify it.
      if (!keepInfo.isMinificationAllowed(method, appView.options())) {
        continue;
      }
      // With desugared library, call-backs names are reserved here.
      if (method.getDefinition().isLibraryMethodOverride().isTrue()) {
        continue;
      }
      // We use the same name for interface names even if it has different types.
      DexClassAndMethod lookupResult =
          appInfo.lookupMaximallySpecificMethod(method.getHolder(), method.getReference());
      if (lookupResult == null) {
        // We cannot rename methods we cannot look up.
        continue;
      }
      String errorString = method.getReference().qualifiedName() + " is not kept but is overloaded";
      assert lookupResult.getHolder().isInterface() : errorString;
      // TODO(b/159113601): Reenable assert.
      assert true || originalName == null || originalName.equals(method.getReference().name)
          : errorString;
      originalName = method.getReference().name;
    }
    return true;
  }

  private static int getMethodStartLine(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (code == null) {
      return 0;
    }
    if (code.isDexCode()) {
      DexDebugInfo dexDebugInfo = code.asDexCode().getDebugInfo();
      return dexDebugInfo == null ? 0 : dexDebugInfo.getStartLine();
    } else if (code.isCfCode()) {
      List<CfInstruction> instructions = code.asCfCode().getInstructions();
      for (CfInstruction instruction : instructions) {
        if (!(instruction instanceof CfPosition)) {
          continue;
        }
        return ((CfPosition) instruction).getPosition().getLine();
      }
    }
    return 0;
  }

  // Sort by startline, then DexEncodedMethod.slowCompare.
  // Use startLine = 0 if no debuginfo.
  public static void sortMethods(List<ProgramMethod> methods) {
    methods.sort(
        (lhs, rhs) -> {
          int lhsStartLine = getMethodStartLine(lhs);
          int rhsStartLine = getMethodStartLine(rhs);
          int startLineDiff = lhsStartLine - rhsStartLine;
          if (startLineDiff != 0) return startLineDiff;
          return DexEncodedMethod.slowCompare(lhs.getDefinition(), rhs.getDefinition());
        });
  }

  public static IdentityHashMap<DexString, List<ProgramMethod>> groupMethodsByRenamedName(
      AppView<?> appView, DexProgramClass clazz) {
    IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
        new IdentityHashMap<>(clazz.getMethodCollection().size());
    for (ProgramMethod programMethod : clazz.programMethods()) {
      // Add method only if renamed, moved, or if it has debug info to map.
      DexMethod method = programMethod.getReference();
      DexString renamedName = appView.getNamingLens().lookupName(method);
      methodsByRenamedName
          .computeIfAbsent(renamedName, key -> new ArrayList<>())
          .add(programMethod);
    }
    return methodsByRenamedName;
  }

  private static class MappedPositionsForClassResult {

    private final DexProgramClass clazz;
    private final List<MappedPositionsForMethodResult> mappedPositionsForMethods;

    private MappedPositionsForClassResult(
        DexProgramClass clazz, List<MappedPositionsForMethodResult> mappedPositionsForMethods) {
      this.clazz = clazz;
      this.mappedPositionsForMethods = mappedPositionsForMethods;
    }

    DexProgramClass getClazz() {
      return clazz;
    }

    List<MappedPositionsForMethodResult> getMappedPositionsForMethods() {
      return mappedPositionsForMethods;
    }
  }

  private static class MappedPositionsForMethodResult {

    private final ProgramMethod method;
    private final List<MappedPosition> mappedPositions;
    private final MethodPositionRemapper positionRemapper;
    private final boolean canUsePc;

    private MappedPositionsForMethodResult(
        ProgramMethod method,
        List<MappedPosition> mappedPositions,
        MethodPositionRemapper positionRemapper,
        boolean canUsePc) {
      this.method = method;
      this.mappedPositions = mappedPositions;
      this.positionRemapper = positionRemapper;
      this.canUsePc = canUsePc;
    }
  }
}
