// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

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
import com.android.tools.r8.ir.optimize.ConstantCanonicalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.IR2LirConverter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.naming.RecordInvokeDynamicInvokeCustomRewriter;
import com.android.tools.r8.optimize.MemberRebindingIdentityLens;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.verticalclassmerging.IncompleteVerticalClassMergerBridgeCode;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class LirConverter {

  public static void enterLirSupportedPhase(
      AppView<AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    assert appView.testing().canUseLir(appView);
    assert appView.testing().isPreLirPhase();
    appView.testing().enterLirSupportedPhase();
    ConstResourceNumberRewriter constResourceNumberRewriter =
        new ConstResourceNumberRewriter(appView);
    // Convert code objects to LIR.
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          // TODO(b/225838009): Also convert instance initializers to LIR, by adding support for
          //  computing the inlining constraint for LIR and using that in the class mergers, and
          //  class initializers, by updating the concatenation of clinits in horizontal class
          //  merging.
          clazz.forEachProgramMethodMatching(
              method ->
                  method.hasCode()
                      && !method.isInstanceInitializer()
                      && !appView.isCfByteCodePassThrough(method),
              method -> {
                IRCode code = method.buildIR(appView, MethodConversionOptions.forLirPhase(appView));
                constResourceNumberRewriter.run(code, Timing.empty());
                LirCode<Integer> lirCode =
                    IR2LirConverter.translate(
                        code,
                        BytecodeMetadataProvider.empty(),
                        LirStrategy.getDefaultStrategy().getEncodingStrategy(),
                        appView.options());
                // TODO(b/312890994): Setting a custom code lens is only needed until we convert
                //  code objects to LIR before we create the first code object with a custom code
                //  lens (horizontal class merging).
                GraphLens codeLens = method.getDefinition().getCode().getCodeLens(appView);
                if (codeLens != appView.codeLens()) {
                  lirCode =
                      new LirCode<>(lirCode) {

                        @Override
                        public boolean hasExplicitCodeLens() {
                          return true;
                        }

                        @Override
                        public GraphLens getCodeLens(AppView<?> appView) {
                          return codeLens;
                        }
                      };
                }
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
    assert appView.testing().canUseLir(appView);
    assert appView.testing().isSupportedLirPhase();
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    assert verifyLirOnly(appView);

    GraphLens graphLens = appView.graphLens();
    assert graphLens.isNonIdentityLens();
    assert appView.codeLens().isAppliedLens() || appView.codeLens().isClearCodeRewritingLens();

    MemberRebindingIdentityLens memberRebindingIdentityLens =
        graphLens.asNonIdentityLens().find(GraphLens::isMemberRebindingIdentityLens);
    assert memberRebindingIdentityLens != null;
    if (graphLens == memberRebindingIdentityLens
        && memberRebindingIdentityLens.getPrevious().isAppliedLens()) {
      // Nothing to rewrite.
      return;
    }

    timing.begin("LIR->LIR@" + graphLens.getClass().getTypeName());
    rewriteLirWithUnappliedLens(appView, executorService);
    timing.end();
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
                m -> rewriteLirMethodWithLens(m, appView, rewriterUtils)),
        appView.options().getThreadingModule(),
        executorService);

    // Clear the reference type cache after conversion to reduce memory pressure.
    appView.dexItemFactory().clearTypeElementsCache();
  }

  private static void rewriteLirMethodWithLens(
      ProgramMethod method,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      LensCodeRewriterUtils rewriterUtils) {
    LirCode<Integer> lirCode = method.getDefinition().getCode().asLirCode();
    LirCode<Integer> rewrittenLirCode = lirCode.rewriteWithLens(method, appView, rewriterUtils);
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
            new DexItemBasedConstStringRemover(appView),
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
    appView.clearCodeRewritings(executorService, timing);
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
    Timing onThreadTiming = Timing.empty();
    IRCode irCode = method.buildIR(appView, MethodConversionOptions.forPostLirPhase(appView));
    assert irCode.verifyInvokeInterface(appView);
    boolean changed = codeRewriterPassCollection.run(irCode, null, null, onThreadTiming);
    if (appView.options().isGeneratingDex() && changed) {
      ConstantCanonicalizer constantCanonicalizer =
          new ConstantCanonicalizer(appView, method, irCode);
      constantCanonicalizer.canonicalize();
    }
    // Processing is done and no further uses of the meta-data should arise.
    BytecodeMetadataProvider noMetadata = BytecodeMetadataProvider.empty();
    // During processing optimization info may cause previously live code to become dead.
    // E.g., we may now have knowledge that an invoke does not have side effects.
    // Thus, we re-run the dead-code remover now as it is assumed complete by CF/DEX finalization.
    deadCodeRemover.run(irCode, onThreadTiming);
    MethodConversionOptions conversionOptions = irCode.getConversionOptions();
    assert !conversionOptions.isGeneratingLir();
    IRFinalizer<?> finalizer = conversionOptions.getFinalizer(deadCodeRemover, appView);
    method.setCode(finalizer.finalizeCode(irCode, noMetadata, onThreadTiming), appView);
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
