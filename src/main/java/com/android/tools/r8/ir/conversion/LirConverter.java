// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.passes.AdaptClassStringsRewriter;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPassCollection;
import com.android.tools.r8.ir.conversion.passes.ConstResourceNumberRemover;
import com.android.tools.r8.ir.conversion.passes.ConstResourceNumberRewriter;
import com.android.tools.r8.ir.conversion.passes.DexItemBasedConstStringRemover;
import com.android.tools.r8.ir.conversion.passes.FilledNewArrayRewriter;
import com.android.tools.r8.ir.conversion.passes.InitClassRemover;
import com.android.tools.r8.ir.conversion.passes.OriginalFieldWitnessRemover;
import com.android.tools.r8.ir.conversion.passes.StoreStoreFenceToInvokeRewriter;
import com.android.tools.r8.ir.conversion.passes.StringSwitchConverter;
import com.android.tools.r8.ir.conversion.passes.StringSwitchRemover;
import com.android.tools.r8.ir.optimize.ConstantCanonicalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.IR2LirConverter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.naming.IdentifierNameStringMarker;
import com.android.tools.r8.naming.RecordInvokeDynamicInvokeCustomRewriter;
import com.android.tools.r8.optimize.MemberRebindingIdentityLens;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingAction;
import com.android.tools.r8.utils.timing.Timing;
import com.android.tools.r8.verticalclassmerging.IncompleteVerticalClassMergerBridgeCode;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class LirConverter {

  // Processing is done and no further uses of the meta-data should arise.
  private static final BytecodeMetadataProvider noMetadata = BytecodeMetadataProvider.empty();

  public static void enterLirSupportedPhase(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException {
    assert appView.testing().canUseLir(appView);
    assert appView.testing().isPreLirPhase();
    appView.testing().enterLirSupportedPhase();
    CodeRewriterPassCollection codeRewriterPassCollection =
        new CodeRewriterPassCollection(
            new ConstResourceNumberRewriter(appView),
            new StringSwitchConverter(appView),
            new IdentifierNameStringMarker(appView));
    // Convert code objects to LIR.
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          clazz.forEachProgramMethodMatching(
              method -> method.hasCode() && !appView.isCfByteCodePassThrough(method),
              method -> {
                assert !method.getDefinition().getCode().hasExplicitCodeLens();
                IRCode code = method.buildIR(appView, MethodConversionOptions.forLirPhase(appView));
                codeRewriterPassCollection.run(
                    code, null, null, Timing.empty(), null, appView.options());
                LirCode<Integer> lirCode =
                    IR2LirConverter.translate(
                        code,
                        BytecodeMetadataProvider.empty(),
                        LirStrategy.getDefaultStrategy().getEncodingStrategy(),
                        appView.options());
                method.setCode(lirCode, appView);
              });
        },
        appView.options().getThreadingModule(),
        executorService);
    // Conversion to LIR via IR will allocate type elements.
    // They are not needed after construction so remove them again.
    appView.dexItemFactory().clearTypeElementsCache();
  }

  public static void rewriteLirWithLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    rewriteLirWithLens(
        appView,
        timing,
        executorService,
        () -> appView.clearCodeRewritings(executorService, timing));
  }

  public static <T extends Throwable> void rewriteLirWithLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Timing timing,
      ExecutorService executorService,
      ThrowingAction<T> onChangedAction)
      throws ExecutionException, T {
    assert appView.testing().canUseLir(appView);
    assert appView.testing().isSupportedLirPhase();
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    assert verifyLirOnly(appView);

    GraphLens graphLens = appView.graphLens();
    assert graphLens.isNonIdentityLens();
    assert appView.codeLens().isAppliedLens()
        || appView.codeLens().isClearCodeRewritingLens()
        || appView.codeLens().isIdentityLens();

    MemberRebindingIdentityLens memberRebindingIdentityLens =
        graphLens.asNonIdentityLens().find(GraphLens::isMemberRebindingIdentityLens);
    assert memberRebindingIdentityLens != null;
    if (graphLens == memberRebindingIdentityLens
        && memberRebindingIdentityLens.getPrevious().isAppliedLens()) {
      // Nothing to rewrite.
      return;
    }

    timing.begin("LIR->LIR@" + graphLens.getClass().getName());
    rewriteLirWithUnappliedLens(appView, executorService);
    timing.end();

    onChangedAction.execute();
  }

  private static void rewriteLirWithUnappliedLens(
      AppView<? extends AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    LensCodeRewriterUtils rewriterUtils = new LensCodeRewriterUtils(appView, true);
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz ->
            clazz.forEachProgramMethodMatching(
                m -> m.hasCode() && m.getCode().isLirCode(),
                m -> rewriteLirMethodWithLens(m, appView, appView.graphLens(), rewriterUtils)),
        appView.options().getThreadingModule(),
        executorService);

    // Clear the reference type cache after conversion to reduce memory pressure.
    appView.dexItemFactory().clearTypeElementsCache();
  }

  public static void rewriteLirMethodWithLens(
      ProgramMethod method,
      AppView<?> appView,
      GraphLens graphLens,
      LensCodeRewriterUtils rewriterUtils) {
    LirCode<Integer> lirCode = method.getDefinition().getCode().asLirCode();
    LirCode<Integer> rewrittenLirCode =
        lirCode.rewriteWithLens(method, appView, graphLens, rewriterUtils);
    if (ObjectUtils.notIdentical(lirCode, rewrittenLirCode)) {
      method.setCode(rewrittenLirCode, appView);
    }
  }

  public static void finalizeLirToOutputFormat(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    assert appView.testing().canUseLir(appView);
    assert appView.testing().isSupportedLirPhase();
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    assert verifyLirOnly(appView);
    appView.testing().exitLirSupportedPhase();
    DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
    String output = appView.options().isGeneratingClassFiles() ? "CF" : "DEX";
    timing.begin("LIR->IR->" + output);
    CodeRewriterPassCollection codeRewriterPassCollection =
        new CodeRewriterPassCollection(
            new AdaptClassStringsRewriter(appView),
            new ConstResourceNumberRemover(appView),
            new StoreStoreFenceToInvokeRewriter(appView),
            new OriginalFieldWitnessRemover(appView),
            // Must run before DexItemBasedConstStringRemover.
            new StringSwitchRemover(appView),
            new DexItemBasedConstStringRemover(appView),
            new InitClassRemover(appView),
            new RecordInvokeDynamicInvokeCustomRewriter(appView),
            new FilledNewArrayRewriter(appView));
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz ->
            clazz.forEachProgramMethod(
                m ->
                    finalizeLirMethodToOutputFormat(
                        m, deadCodeRemover, appView, codeRewriterPassCollection)),
        appView.options().getThreadingModule(),
        executorService);
    timing.end();
    // Clear the reference type cache after conversion to reduce memory pressure.
    appView.dexItemFactory().clearTypeElementsCache();
    // At this point all code has been mapped according to the graph lens.
    assert appView.graphLens().isMemberRebindingIdentityLens()
        && appView.graphLens().asMemberRebindingIdentityLens().getPrevious() == appView.codeLens();
  }

  private static void finalizeLirMethodToOutputFormat(
      ProgramMethod method,
      DeadCodeRemover deadCodeRemover,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      CodeRewriterPassCollection codeRewriterPassCollection) {
    Code code = method.getDefinition().getCode();
    if (!(code instanceof LirCode)) {
      return;
    }
    IRConverter.printMethod(method, "LIR before output format", appView.options());
    Timing onThreadTiming = Timing.empty();
    IRCode irCode = method.buildIR(appView, MethodConversionOptions.forPostLirPhase(appView));
    assert irCode.verifyInvokeInterface(appView);
    String previous = IRConverter.printMethodIR(irCode, "IR from LIR", "", appView.options());
    Pair<Boolean, String> result =
        codeRewriterPassCollection.run(
            irCode, null, null, onThreadTiming, previous, appView.options());
    boolean changed = result.getFirst();
    previous = result.getSecond();
    if (appView.options().isGeneratingDex() && changed) {
      ConstantCanonicalizer constantCanonicalizer =
          new ConstantCanonicalizer(appView, method, irCode);
      constantCanonicalizer.canonicalize();
    }
    // During processing optimization info may cause previously live code to become dead.
    // E.g., we may now have knowledge that an invoke does not have side effects.
    // Thus, we re-run the dead-code remover now as it is assumed complete by CF/DEX finalization.
    deadCodeRemover.run(irCode, onThreadTiming);
    previous = IRConverter.printMethodIR(irCode, "IR before finalize", previous, appView.options());
    MethodConversionOptions conversionOptions = irCode.getConversionOptions();
    assert !conversionOptions.isGeneratingLir();
    IRFinalizer<?> finalizer = conversionOptions.getFinalizer(deadCodeRemover, appView);
    method.setCode(finalizer.finalizeCode(irCode, noMetadata, onThreadTiming, previous), appView);
    IRConverter.printMethod(method, "Finalized output format", appView.options());
  }

  public static void finalizeClasspathLirToOutputFormat(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    InternalOptions options = appView.options();
    if (options.partialSubCompilationConfiguration == null) {
      return;
    }

    timing.begin("Commit classpath classes to program");
    R8PartialR8SubCompilationConfiguration subCompilationConfiguration =
        options.partialSubCompilationConfiguration.asR8();
    subCompilationConfiguration.commitDexingOutputClasses(appView);
    timing.end();

    String output = appView.options().isGeneratingClassFiles() ? "CF" : "DEX";
    timing.begin("LIR->IR->" + output + " (D8)");
    AppInfo d8AppInfo =
        AppInfo.createInitialAppInfo(
            appView.app(),
            GlobalSyntheticsStrategy.forNonSynthesizing(),
            appView.appInfo().getClassToFeatureSplitMap());
    AppView<AppInfo> d8AppView = AppView.createForD8(d8AppInfo);
    DeadCodeRemover deadCodeRemover = new DeadCodeRemover(d8AppView);
    CodeRewriterPassCollection codeRewriterPassCollection =
        new CodeRewriterPassCollection(
            new StringSwitchRemover(d8AppView), new FilledNewArrayRewriter(d8AppView));
    ThreadUtils.processItems(
        subCompilationConfiguration.getDexingOutputClasses(),
        clazz ->
            clazz.forEachProgramMethod(
                m ->
                    finalizeClasspathLirMethodToOutputFormat(
                        m, d8AppView, deadCodeRemover, codeRewriterPassCollection)),
        options.getThreadingModule(),
        executorService);
    timing.end();

    // Clear the reference type cache after conversion to reduce memory pressure.
    appView.dexItemFactory().clearTypeElementsCache();
  }

  private static void finalizeClasspathLirMethodToOutputFormat(
      ProgramMethod method,
      AppView<AppInfo> appView,
      DeadCodeRemover deadCodeRemover,
      CodeRewriterPassCollection codeRewriterPassCollection) {
    Code code = method.getDefinition().getCode();
    if (!(code instanceof LirCode)) {
      return;
    }
    IRCode irCode = method.buildIR(appView, MethodConversionOptions.forPostLirPhase(appView));
    MethodConversionOptions conversionOptions = irCode.getConversionOptions();
    assert conversionOptions.isGeneratingDex();
    codeRewriterPassCollection.run(irCode, null, null, Timing.empty(), null, appView.options());
    deadCodeRemover.run(irCode, Timing.empty());
    IRFinalizer<?> finalizer = conversionOptions.getFinalizer(deadCodeRemover, appView);
    method.setCode(finalizer.finalizeCode(irCode, noMetadata, Timing.empty()), appView);
  }

  public static boolean verifyLirOnly(AppView<? extends AppInfoWithClassHierarchy> appView) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod method : clazz.methods(DexEncodedMethod::hasCode)) {
        assert method.getCode().isLirCode()
            || method.getCode().isSharedCodeObject()
            || method.getCode() instanceof IncompleteVerticalClassMergerBridgeCode
            || appView.isCfByteCodePassThrough(method)
            || appView.options().skipIR;
      }
    }
    return true;
  }
}
