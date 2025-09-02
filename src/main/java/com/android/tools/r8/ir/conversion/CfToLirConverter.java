// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FinishedEnqueuerAnalysis;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.passes.BranchSimplifier;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPassCollection;
import com.android.tools.r8.ir.conversion.passes.ConstResourceNumberRewriter;
import com.android.tools.r8.ir.conversion.passes.StringSwitchConverter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.membervaluepropagation.D8MemberValuePropagation;
import com.android.tools.r8.lightir.IR2LirConverter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.naming.IdentifierNameStringMarker;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class CfToLirConverter implements FinishedEnqueuerAnalysis {

  private final AppView<AppInfoWithClassHierarchy> appView;
  private final CodeRewriterPassCollection codeRewriterPassCollection;
  private final Enqueuer enqueuer;

  public CfToLirConverter(AppView<AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.codeRewriterPassCollection =
        new CodeRewriterPassCollection(
            new ConstResourceNumberRewriter(appView), new StringSwitchConverter(appView));
    this.enqueuer = enqueuer;
  }

  public static CfToLirConverter register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      EnqueuerAnalysisCollection.Builder builder) {
    // TODO(b/439952010): Also enable LIR for CF. This is currently blocked by not knowing which
    //  methods require passthrough.
    if (enqueuer.getMode().isInitialTreeShaking() && appView.options().isGeneratingDex()) {
      assert appView.testing().canUseLir(appView);
      assert appView.testing().isPreLirPhase();
      appView.testing().enterLirSupportedPhase();
      CfToLirConverter cfToLirConverter =
          new CfToLirConverter(appView.withClassHierarchy(), enqueuer);
      builder.addFinishedAnalysis(cfToLirConverter);
      return cfToLirConverter;
    }
    return null;
  }

  public void processMethod(ProgramMethod method) {
    if (enqueuer.getWorklist().isNonPushable()) {
      // Post processing desugaring that is generating CF instead of LIR.
      // TODO(b/439952010): Avoid single-threaded LIR conversion during post processing.
      assert verifyIsCfCodeFromPostProcessingDesugaring(method);
      convert(method);
      enqueuer.traceCode(method, Timing.empty());
      return;
    }
    enqueuer
        .getTaskCollection()
        .submitEnqueuerDependentTask(
            () -> {
              convert(method);
              enqueuer.getWorklist().enqueueTraceCodeAction(method);
              return null;
            });
  }

  private boolean verifyIsCfCodeFromPostProcessingDesugaring(ProgramMethod method) {
    assert method.getDefinition().getCode().isCfCode();
    assert method.getDefinition().isD8R8Synthesized();
    CfCode code = method.getDefinition().getCode().asCfCode();
    if (ListUtils.last(code.getInstructions()).isThrow()) {
      // This is a throw stub.
      return true;
    }
    if (code.getInstructions().stream()
        .filter(CfInstruction::isInvokeInterface)
        .map(i -> i.asInvoke().getMethod().getHolderType())
        .anyMatch(
            type ->
                type.isIdenticalTo(
                    appView.dexItemFactory().javaUtilConcurrentExecutorServiceType))) {
      // This is an ExecutorService stub created by synthesizeExecutorServiceDispatchCase().
      return true;
    }
    return false;
  }

  private void convert(ProgramMethod method) {
    assert method.getDefinition().hasCode();
    assert !method.getDefinition().getCode().hasExplicitCodeLens();
    assert !appView.isCfByteCodePassThrough(method);
    // TODO(b/414965524): Remove the need for checking processed and move the handling
    //  synchronized methods in DEX to a "CodeRewriterPass" as IR rewriting.
    if (method.getDefinition().isProcessed()) {
      assert appView.options().partialSubCompilationConfiguration != null;
      assert appView.options().partialSubCompilationConfiguration.isR8();
      method.getDefinition().markNotProcessed();
    }
    IRCode code = method.buildIR(appView, MethodConversionOptions.forLirPhase(appView));
    codeRewriterPassCollection.run(code, null, null, Timing.empty(), null, appView.options());
    if (appView.options().isGeneratingDex() && isCodeReadingSdkInt(code)) {
      new D8MemberValuePropagation(appView).run(code);
      new BranchSimplifier(appView).simplifyIf(code);
      new DeadCodeRemover(appView).run(code, Timing.empty());
    }
    LirCode<Integer> lirCode =
        IR2LirConverter.translate(
            code,
            BytecodeMetadataProvider.empty(),
            LirStrategy.getDefaultStrategy().getEncodingStrategy(),
            appView.options());
    method.setCode(lirCode, appView);
  }

  private boolean isCodeReadingSdkInt(IRCode code) {
    DexField SDK_INT = appView.dexItemFactory().androidOsBuildVersionMembers.SDK_INT;
    for (StaticGet staticGet : code.<StaticGet>instructions(Instruction::isStaticGet)) {
      if (staticGet.getField().isIdenticalTo(SDK_INT)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void done(Enqueuer enqueuer, ExecutorService executorService) throws ExecutionException {
    // Process identifier name strings.
    if (appView.options().isMinifying()) {
      DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
      IdentifierNameStringMarker identifierNameStringMarker =
          new IdentifierNameStringMarker(appView, enqueuer);
      ThreadUtils.processItems(
          appView.appInfo().classes(),
          clazz -> processIdentifierNameStrings(clazz, deadCodeRemover, identifierNameStringMarker),
          appView.options().getThreadingModule(),
          executorService);
    }

    // Conversion to LIR via IR will allocate type elements.
    // They are not needed after construction so remove them again.
    appView.dexItemFactory().clearTypeElementsCache();
  }

  private void processIdentifierNameStrings(
      DexProgramClass clazz,
      DeadCodeRemover deadCodeRemover,
      IdentifierNameStringMarker identifierNameStringMarker) {
    MutableMethodConversionOptions conversionOptions = MethodConversionOptions.forLirPhase(appView);
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasLirCode,
        method -> {
          IRCode code = method.buildIR(appView, conversionOptions);
          identifierNameStringMarker.run(code, Timing.empty());
          method.setCode(
              conversionOptions
                  .getFinalizer(deadCodeRemover, appView)
                  .finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty()),
              appView);
        });
  }
}
