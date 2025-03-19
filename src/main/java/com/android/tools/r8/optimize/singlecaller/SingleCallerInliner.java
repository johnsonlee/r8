// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.singlecaller;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.InnerClassAttribute;
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
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Deque;
import java.util.List;
import java.util.Set;
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
    return appView.options().getSingleCallerInlinerOptions().isEnabled();
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    ProgramMethodSet monomorphicVirtualMethods =
        computeMonomorphicVirtualRootMethods(appView, executorService);
    ProgramMethodMap<ProgramMethod> singleCallerMethods =
        new SingleCallerScanner(appView, monomorphicVirtualMethods)
            .getSingleCallerMethods(executorService);
    if (singleCallerMethods.isEmpty()) {
      return;
    }
    Inliner inliner = new SingleCallerInlinerImpl(appView, singleCallerMethods);
    processCallees(inliner, singleCallerMethods, executorService);
    performInlining(inliner, singleCallerMethods, executorService);
    pruneEnclosingMethodAttributes();
    pruneItems(singleCallerMethods, executorService);
  }

  // We only allow single caller inlining of "direct dispatch virtual methods". We currently only
  // deal with (rooted) virtual methods that do not override abstract/interface methods. In order to
  // also deal with virtual methods that override abstract/interface methods we would need to record
  // calls to the abstract/interface methods as calls to the non-abstract virtual method.
  public static ProgramMethodSet computeMonomorphicVirtualRootMethods(
      AppView<? extends AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> stronglyConnectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    return MonomorphicVirtualMethodsAnalysis.computeMonomorphicVirtualRootMethods(
        appView, immediateSubtypingInfo, stronglyConnectedComponents, executorService);
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
            IRCode code = method.buildIR(appView, MethodConversionOptions.forLirPhase(appView));
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
      public void notifyMethodInlined(ProgramMethod caller, ProgramMethod callee) {}

      @Override
      public void unsetCallSiteInformation(ProgramMethod method) {
        throw new Unreachable();
      }
    };
  }

  // TODO(b/335124741): Determine what to do when single caller inlining the enclosing method
  //  of a class.
  private void pruneEnclosingMethodAttributes() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (!clazz.hasEnclosingMethodAttribute()) {
        continue;
      }
      EnclosingMethodAttribute enclosingMethodAttribute = clazz.getEnclosingMethodAttribute();
      if (!enclosingMethodAttribute.hasEnclosingMethod()) {
        continue;
      }
      DexMethod enclosingMethodReference = enclosingMethodAttribute.getEnclosingMethod();
      DexClassAndMethod enclosingMethod = appView.definitionFor(enclosingMethodReference);
      if (enclosingMethod == null
          || !enclosingMethod.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite()) {
        continue;
      }

      // Remove enclosing method attribute and rewrite the inner class attribute.
      clazz.clearEnclosingMethodAttribute();

      InnerClassAttribute innerClassAttributeForThisClass =
          clazz.getInnerClassAttributeForThisClass();
      if (innerClassAttributeForThisClass != null) {
        assert innerClassAttributeForThisClass.getOuter() == null;
        InnerClassAttribute replacement =
            new InnerClassAttribute(
                innerClassAttributeForThisClass.getAccess(),
                innerClassAttributeForThisClass.getInner(),
                enclosingMethod.getHolderType(),
                innerClassAttributeForThisClass.getInnerName());
        clazz.replaceInnerClassAttributeForThisClass(replacement);
      }
    }
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
        IRCode code,
        ProgramMethod method,
        MethodProcessor methodProcessor,
        InliningReasonStrategy inliningReasonStrategy) {
      return new DefaultInliningOracle(
          appView, method, methodProcessor, inliningReasonStrategy, code) {

        @Override
        public InlineResult computeInlining(
            IRCode code,
            InvokeMethod invoke,
            SingleResolutionResult<?> resolutionResult,
            ProgramMethod singleTarget,
            ProgramMethod context,
            ClassInitializationAnalysis classInitializationAnalysis,
            InliningIRProvider inliningIRProvider,
            Timing timing,
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
              timing,
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
