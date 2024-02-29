// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.singlecaller;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.InliningReasonStrategy;
import com.android.tools.r8.ir.optimize.inliner.NopWhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class SingleCallerInliner {

  private final AppView<AppInfoWithLiveness> appView;

  public SingleCallerInliner(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void runIfNecessary(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (shouldRun()) {
      timing.begin("SingleCallerInliner");
      run(executorService);
      timing.end();
    }
  }

  private boolean shouldRun() {
    InternalOptions options = appView.options();
    return !options.debug
        && !options.intermediate
        && options.isOptimizing()
        && options.isShrinking();
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    ProgramMethodMap<ProgramMethod> singleCallerMethods =
        new SingleCallerScanner(appView).getSingleCallerMethods(executorService);
    Inliner inliner = new SingleCallerInlinerImpl(appView, singleCallerMethods);
    processCallees(inliner, singleCallerMethods, executorService);
    performInlining(inliner, singleCallerMethods, executorService);
    pruneItems(singleCallerMethods, executorService);
  }

  private void processCallees(
      Inliner inliner,
      ProgramMethodMap<ProgramMethod> singleCallerMethods,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        singleCallerMethods.streamKeys()::forEach,
        callee -> {
          IRCode code = callee.buildIR(appView);
          getSimpleFeedback()
              .markProcessed(callee.getDefinition(), inliner.computeInliningConstraint(code));
          // TODO(b/325199754): Do not tamper with the code API level.
          if (callee.getDefinition().getApiLevelForCode().isNotSetApiLevel()) {
            callee
                .getDefinition()
                .setApiLevelForCode(
                    appView.apiLevelCompute().computeInitialMinApiLevel(appView.options()));
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private void performInlining(
      Inliner inliner,
      ProgramMethodMap<ProgramMethod> singleCallerMethods,
      ExecutorService executorService)
      throws ExecutionException {
    CallSiteInformation callSiteInformation = createCallSiteInformation(singleCallerMethods);
    Deque<ProgramMethodSet> waves = SingleCallerWaves.buildWaves(appView, singleCallerMethods);
    while (!waves.isEmpty()) {
      ProgramMethodSet wave = waves.removeFirst();
      OneTimeMethodProcessor methodProcessor =
          OneTimeMethodProcessor.create(wave, MethodProcessorEventConsumer.empty(), appView);
      methodProcessor.setCallSiteInformation(callSiteInformation);
      methodProcessor.forEachWaveWithExtension(
          (method, methodProcessingContext) -> {
            // TODO(b/325199754): Do not tamper with the code API level.
            if (method.getDefinition().getApiLevelForCode().isNotSetApiLevel()) {
              method
                  .getDefinition()
                  .setApiLevelForCode(
                      appView.apiLevelCompute().computeInitialMinApiLevel(appView.options()));
            }
            IRCode code =
                method.buildIR(
                    appView,
                    MethodConversionOptions.forLirPhase(appView).disableStringSwitchConversion());
            inliner.performInlining(
                method, code, getSimpleFeedback(), methodProcessor, Timing.empty());
            CodeRewriter.removeAssumeInstructions(appView, code);
            LirCode<Integer> lirCode =
                new IRToLirFinalizer(appView)
                    .finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
            method.setCode(lirCode, appView);
            // Recompute inlining constraints if this method is also a single caller callee.
            if (singleCallerMethods.containsKey(method)) {
              getSimpleFeedback()
                  .markProcessed(method.getDefinition(), inliner.computeInliningConstraint(code));
            }
          },
          appView.options().getThreadingModule(),
          executorService);
    }
  }

  private CallSiteInformation createCallSiteInformation(
      ProgramMethodMap<ProgramMethod> singleCallerMethods) {
    return new CallSiteInformation() {
      @Override
      public boolean hasSingleCallSite(ProgramMethod method, ProgramMethod context) {
        return singleCallerMethods.containsKey(method);
      }

      @Override
      public boolean hasSingleCallSite(ProgramMethod method) {
        return singleCallerMethods.containsKey(method);
      }

      @Override
      public boolean isMultiCallerInlineCandidate(ProgramMethod method) {
        return false;
      }

      @Override
      public void unsetCallSiteInformation(ProgramMethod method) {
        throw new Unreachable();
      }
    };
  }

  private void pruneItems(
      ProgramMethodMap<ProgramMethod> singleCallerMethods, ExecutorService executorService)
      throws ExecutionException {
    PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder().setPrunedApp(appView.app());
    singleCallerMethods.forEach(
        (callee, caller) -> {
          if (callee.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite()) {
            prunedItemsBuilder.addFullyInlinedMethod(callee.getReference(), caller);
            callee.getHolder().removeMethod(callee.getReference());
          }
        });
    PrunedItems prunedItems = prunedItemsBuilder.build();
    appView.pruneItems(prunedItems, executorService, Timing.empty());
  }

  private static class SingleCallerInlinerImpl extends Inliner {

    private final ProgramMethodMap<ProgramMethod> singleCallerMethods;

    SingleCallerInlinerImpl(
        AppView<AppInfoWithLiveness> appView, ProgramMethodMap<ProgramMethod> singleCallerMethods) {
      super(appView);
      this.singleCallerMethods = singleCallerMethods;
    }

    @Override
    public DefaultInliningOracle createDefaultOracle(
        ProgramMethod method,
        MethodProcessor methodProcessor,
        int inliningInstructionAllowance,
        InliningReasonStrategy inliningReasonStrategy) {
      return new DefaultInliningOracle(
          appView, inliningReasonStrategy, method, methodProcessor, inliningInstructionAllowance) {

        @Override
        public InlineResult computeInlining(
            IRCode code,
            InvokeMethod invoke,
            SingleResolutionResult<?> resolutionResult,
            ProgramMethod singleTarget,
            ProgramMethod context,
            ClassInitializationAnalysis classInitializationAnalysis,
            InliningIRProvider inliningIRProvider,
            WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
          if (!singleCallerMethods.containsKey(singleTarget)) {
            return null;
          }
          return super.computeInlining(
              code,
              invoke,
              resolutionResult,
              singleTarget,
              context,
              classInitializationAnalysis,
              inliningIRProvider,
              whyAreYouNotInliningReporter);
        }
      };
    }

    @Override
    public WhyAreYouNotInliningReporter createWhyAreYouNotInliningReporter(
        ProgramMethod singleTarget, ProgramMethod context) {
      return NopWhyAreYouNotInliningReporter.getInstance();
    }
  }
}
