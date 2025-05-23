// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.AndroidApiLevelDatabaseHelper;
import com.android.tools.r8.androidapi.AndroidApiUnknownReferenceDiagnosticHelper;
import com.android.tools.r8.androidapi.ApiReferenceStubber;
import com.android.tools.r8.androidapi.ApiReferenceStubberEventConsumer;
import com.android.tools.r8.androidapi.ComputedApiLevel.KnownApiLevel;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.FieldCollection.FieldCollectionFactory;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.graph.NestHostClassAttribute;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ThrowExceptionCode;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.ir.desugar.LambdaClass;
import com.android.tools.r8.ir.desugar.lambda.LambdaDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordTagSynthesizer;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaring;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaringEventConsumer;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.naming.RecordRewritingNamingLens;
import com.android.tools.r8.naming.VarHandleDesugaringRewritingNamingLens;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticFinalization;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ReachabilitySensitiveValue;
import com.android.tools.r8.utils.SelfRetraceTest;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * The GlobalSyntheticsGenerator, a tool for generating a dex file for all possible global
 * synthetics.
 */
@KeepForApi
public class GlobalSyntheticsGenerator {

  private static boolean ensureAllGlobalSyntheticsModeled(SyntheticNaming naming) {
    for (SyntheticKind kind : naming.kinds()) {
      assert !kind.isGlobal()
          || !kind.isMayOverridesNonProgramType()
          || kind.equals(naming.RECORD_TAG)
          || kind.equals(naming.API_MODEL_STUB)
          || kind.equals(naming.METHOD_HANDLES_LOOKUP)
          || kind.equals(naming.VAR_HANDLE)
          || kind.equals(naming.LAMBDA_METHOD_ANNOTATION);
    }
    return true;
  }

  /**
   * Main API entry for the global synthetics generator.
   *
   * @param command GlobalSyntheticsGenerator command.
   */
  public static void run(GlobalSyntheticsGeneratorCommand command)
      throws CompilationFailedException {
    runForTesting(command.getInputApp(), command.getInternalOptions());
  }

  /**
   * Main API entry for the global synthetics generator.
   *
   * @param command GlobalSyntheticsGenerator command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   */
  public static void run(GlobalSyntheticsGeneratorCommand command, ExecutorService executor)
      throws CompilationFailedException {
    run(command.getInputApp(), command.getInternalOptions(), executor);
  }

  static void runForTesting(AndroidApp app, InternalOptions options)
      throws CompilationFailedException {
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    run(app, options, executorService);
  }

  private static void run(AndroidApp app, InternalOptions options, ExecutorService executorService)
      throws CompilationFailedException {
    try {
      ExceptionUtils.withCompilationHandler(
          options.reporter,
          () -> {
            Timing timing = Timing.create("GlobalSyntheticsGenerator " + Version.LABEL, options);
            try {
              timing.begin("Read input app");
              AppView<AppInfo> appView = readApp(app, options, executorService, timing);
              timing.end();

              timing.begin("Create global synthetics");
              createGlobalSynthetics(appView, timing, executorService);
              timing.end();

              assert GlobalSyntheticsGeneratorVerifier.verifyExpectedClassesArePresent(appView);
              if (options.isGeneratingDex()) {
                ApplicationWriter.create(appView, options.getMarker()).write(executorService, app);
              } else {
                assert options.isGeneratingClassFiles();
                new CfApplicationWriter(appView, options.getMarker())
                    .write(options.getClassFileConsumer(), executorService, app);
              }
            } catch (ExecutionException e) {
              throw unwrapExecutionException(e);
            } catch (IOException e) {
              throw new CompilationError(e.getMessage(), e);
            } finally {
              options.signalFinishedToConsumers();
              // Dump timings.
              if (options.isPrintTimesReportingEnabled()) {
                timing.report();
              }
            }
          });
    } finally {
      executorService.shutdown();
    }
  }

  private static AppView<AppInfo> readApp(
      AndroidApp inputApp, InternalOptions options, ExecutorService executor, Timing timing)
      throws IOException {
    timing.begin("Application read");
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, timing);
    DirectMappedDexApplication app = applicationReader.read(executor).toDirect();
    timing.end();
    AppInfo appInfo =
        timing.time(
            "Create app-info",
            () ->
                AppInfo.createInitialAppInfo(app, GlobalSyntheticsStrategy.forSingleOutputMode()));
    // Now that the dex-application is fully loaded, close any internal archive providers.
    inputApp.closeInternalArchiveProviders();
    return timing.time("Create app-view", () -> AppView.createForD8(appInfo, timing));
  }

  private static void createGlobalSynthetics(
      AppView<AppInfo> appView, Timing timing, ExecutorService executorService)
      throws ExecutionException, IOException {
    assert ensureAllGlobalSyntheticsModeled(appView.getSyntheticItems().getNaming());
    Set<DexProgramClass> synthesizingContext =
        ImmutableSet.of(createSynthesizingContext(appView.dexItemFactory()));

    // Add global synthetic class for records.
    RecordTagSynthesizer.ensureRecordClassHelper(
        appView, synthesizingContext, RecordDesugaringEventConsumer.empty(), null, null);

    VarHandleDesugaringEventConsumer varHandleEventConsumer =
        VarHandleDesugaringEventConsumer.empty();

    // Add global synthetic class for var handles.
    VarHandleDesugaring.ensureVarHandleClass(appView, varHandleEventConsumer, synthesizingContext);

    // Add global synthetic class for method handles lookup.
    VarHandleDesugaring.ensureMethodHandlesLookupClass(
        appView, varHandleEventConsumer, synthesizingContext);

    // Add global synthetic class for annotating lambda classes.
    LambdaDesugaringEventConsumer emptyLambdaDesugaringEventConsumer =
        new LambdaDesugaringEventConsumer() {
          @Override
          public void acceptLambdaClass(LambdaClass lambdaClass, ProgramMethod context) {
            assert false;
          }

          @Override
          public void acceptLambdaMethodAnnotationDesugaringClass(DexProgramClass clazz) {}

          @Override
          public void acceptLambdaMethodAnnotationDesugaringClassContext(
              DexProgramClass clazz, ProgramDefinition context) {}
        };

    LambdaClass.ensureLambdaMethodAnnotationClass(
        appView, emptyLambdaDesugaringEventConsumer, synthesizingContext);

    // Commit all the synthetics to the program and then convert as per D8.
    // We must run proper D8 conversion as the global synthetics may give rise to additional
    // synthetics as part of their implementation.
    assert appView.getSyntheticItems().hasPendingSyntheticClasses();
    appView.rebuildAppInfo();

    new PrimaryD8L8IRConverter(appView, Timing.empty()).convert(appView, executorService);

    appView.rebuildAppInfo();

    timing.time(
        "Finalize synthetics",
        () -> SyntheticFinalization.finalize(appView, timing, executorService));

    RecordRewritingNamingLens.commitRecordRewritingNamingLens(appView);
    appView.setNamingLens(
        VarHandleDesugaringRewritingNamingLens.createVarHandleDesugaringRewritingNamingLens(
            appView));

    if (appView.options().isGeneratingDex()) {
      // Add global synthetic classes for api stubs.
      createAllApiStubs(appView, synthesizingContext, executorService);
    }

    appView.rebuildAppInfo();
  }

  private static DexProgramClass createSynthesizingContext(DexItemFactory factory) {
    return new DexProgramClass(
        factory.createType("Lcom/android/tools/r8/GlobalSynthetics$$SynthesizingContext;"),
        Kind.CF,
        Origin.unknown(),
        ClassAccessFlags.fromCfAccessFlags(1057),
        factory.objectType,
        DexTypeList.empty(),
        factory.createString("GlobalSynthetics$$SynthesizingContext.java"),
        NestHostClassAttribute.none(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        EnclosingMethodAttribute.none(),
        Collections.emptyList(),
        ClassSignature.noSignature(),
        DexAnnotationSet.empty(),
        FieldCollectionFactory.empty(),
        MethodCollectionFactory.empty(),
        factory.getSkipNameValidationForTesting(),
        DexProgramClass::invalidChecksumRequest,
        ReachabilitySensitiveValue.DISABLED);
  }

  private static void createAllApiStubs(
      AppView<?> appView, Set<DexProgramClass> synthesizingContext, ExecutorService executorService)
      throws ExecutionException {
    AndroidApiLevelCompute apiLevelCompute = appView.apiLevelCompute();

    Set<String> notModeledTypes = AndroidApiLevelDatabaseHelper.notModeledTypes();

    DexItemFactory factory = appView.dexItemFactory();
    ThrowExceptionCode throwExceptionCode =
        ThrowExceptionCode.create(appView.dexItemFactory().noClassDefFoundErrorType);
    ApiReferenceStubberEventConsumer apiReferenceStubberEventConsumer =
        ApiReferenceStubberEventConsumer.empty();
    ThreadUtils.processItems(
        appView.app().asDirect().libraryClasses(),
        libraryClass -> {
          if (notModeledTypes.contains(libraryClass.getClassReference().getTypeName())) {
            return;
          }
          if (ApiReferenceStubber.isNeverStubbedType(libraryClass.getType(), factory)) {
            return;
          }
          KnownApiLevel knownApiLevel =
              apiLevelCompute
                  .computeApiLevelForLibraryReference(libraryClass.getReference())
                  .asKnownApiLevel();
          if (knownApiLevel == null) {
            appView
                .reporter()
                .warning(
                    AndroidApiUnknownReferenceDiagnosticHelper.createInternal(
                        libraryClass.getReference()));
            return;
          }
          if (knownApiLevel.getApiLevel().isLessThanOrEqualTo(appView.options().getMinApiLevel())) {
            return;
          }
          if (libraryClass.isFinal() && !isExceptionType(appView, libraryClass)) {
            return;
          }
          ApiReferenceStubber.mockMissingLibraryClass(
              appView,
              ignored -> synthesizingContext,
              libraryClass,
              throwExceptionCode,
              apiReferenceStubberEventConsumer);
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean isExceptionType(AppView<?> appView, DexLibraryClass libraryClass) {
    DexType throwableType = appView.dexItemFactory().throwableType;
    DexType currentType = libraryClass.getType();
    while (currentType != null) {
      if (currentType == throwableType) {
        return true;
      }
      DexClass superClass = appView.appInfo().definitionForWithoutExistenceAssert(currentType);
      currentType = superClass == null ? null : superClass.getSuperType();
    }
    return false;
  }

  private static void run(String[] args) throws CompilationFailedException {
    GlobalSyntheticsGeneratorCommand command =
        GlobalSyntheticsGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      SelfRetraceTest.test();
      System.out.println(GlobalSyntheticsGeneratorCommandParser.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("GlobalSyntheticsGenerator " + Version.getVersionString());
      return;
    }
    run(command);
  }

  /**
   * Command-line entry to GlobalSynthetics.
   *
   * <p>See {@link GlobalSyntheticsGeneratorCommandParser#getUsageMessage()} or run {@code
   * globalsyntheticsgenerator --help} for usage information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines(
              "Invalid invocation.", GlobalSyntheticsGeneratorCommandParser.getUsageMessage()));
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
