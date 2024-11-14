// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessorWithWave;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.classhierarchy.MethodOverridesCollector;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public abstract class CallSiteInformation {

  /**
   * Check if the <code>method</code> is guaranteed to only have a single call site.
   *
   * <p>For pinned methods (methods kept through Proguard keep rules) this will always answer <code>
   * false</code>.
   */
  public abstract boolean hasSingleCallSite(ProgramMethod method, ProgramMethod context);

  /**
   * Checks if the given method only has a single call without considering context.
   *
   * <p>For pinned methods (methods kept through Proguard keep rules) and methods that override a
   * library method this always returns false.
   */
  public abstract boolean hasSingleCallSite(ProgramMethod method);

  public abstract boolean isMultiCallerInlineCandidate(ProgramMethod method);

  public abstract void notifyMethodInlined(ProgramMethod caller, ProgramMethod callee);

  public abstract void unsetCallSiteInformation(ProgramMethod method);

  public static CallSiteInformation empty() {
    return EmptyCallSiteInformation.EMPTY_INFO;
  }

  private static class EmptyCallSiteInformation extends CallSiteInformation {

    private static final EmptyCallSiteInformation EMPTY_INFO = new EmptyCallSiteInformation();

    @Override
    public boolean hasSingleCallSite(ProgramMethod method, ProgramMethod context) {
      return false;
    }

    @Override
    public boolean hasSingleCallSite(ProgramMethod method) {
      return false;
    }

    @Override
    public boolean isMultiCallerInlineCandidate(ProgramMethod method) {
      return false;
    }

    @Override
    public void notifyMethodInlined(ProgramMethod caller, ProgramMethod callee) {}

    @Override
    public void unsetCallSiteInformation(ProgramMethod method) {
      // Intentionally empty.
    }
  }

  static class CallGraphBasedCallSiteInformation extends CallSiteInformation {

    private final MethodProcessorWithWave methodProcessor;

    // Single callers track their calling context to ensure that the predicate is stable after
    // inlining of the caller.
    private final MutableBidirectionalManyToOneMap<DexMethod, DexMethod> singleCallerMethods =
        BidirectionalManyToOneHashMap.newIdentityHashMap();
    private final Set<DexMethod> multiCallerInlineCandidates = Sets.newIdentityHashSet();
    private final Set<DexMethod> noSingleCallerInliningInto = ConcurrentHashMap.newKeySet();

    CallGraphBasedCallSiteInformation(
        AppView<AppInfoWithLiveness> appView,
        CallGraph graph,
        MethodProcessorWithWave methodProcessor) {
      this.methodProcessor = methodProcessor;

      InternalOptions options = appView.options();
      Predicate<ProgramMethod> pinnedPredicate;
      if (options.isOptimizing() && options.isShrinking()) {
        ProgramMethodSet pinnedMethods =
            MethodOverridesCollector.findAllMethodsAndOverridesThatMatches(
                appView,
                ImmediateProgramSubtypingInfo.create(appView),
                appView.appInfo().classes(),
                method -> {
                  KeepMethodInfo keepInfo = appView.getKeepInfo(method);
                  return !keepInfo.isClosedWorldReasoningAllowed(options, method)
                      || keepInfo.isPinned(options, method);
                });
        pinnedPredicate = pinnedMethods::contains;
      } else {
        pinnedPredicate =
            method -> {
              MethodOptimizationInfo optimizationInfo = method.getOptimizationInfo();
              if (optimizationInfo.shouldSingleCallerInlineIntoSyntheticLambdaAccessor()) {
                KeepMethodInfo keepInfo = appView.getKeepInfo(method);
                assert keepInfo.isClosedWorldReasoningAllowed(options, method);
                assert !keepInfo.isPinned(options, method);
                return false;
              }
              return true;
            };
      }

      for (Node node : graph.getNodes()) {
        ProgramMethod method = node.getProgramMethod();
        DexMethod reference = method.getReference();

        // For non-pinned methods and methods that override library methods we do not know the exact
        // number of call sites.
        if (pinnedPredicate.test(method)) {
          continue;
        }

        if (appView.options().inlinerOptions().disableInliningOfLibraryMethodOverrides
            && method.getDefinition().isLibraryMethodOverride().isTrue()) {
          continue;
        }

        if (method.getDefinition().isDefaultInstanceInitializer()
            && appView.hasProguardCompatibilityActions()
            && appView.getProguardCompatibilityActions().isCompatInstantiated(method.getHolder())) {
          continue;
        }

        int numberOfCallSites = node.getNumberOfCallSites();
        if (numberOfCallSites == 1) {
          if (!appView.getKeepInfo(method).isSingleCallerInliningAllowed(options, method)) {
            continue;
          }
          if (methodProcessor.isPostMethodProcessor()) {
            SyntheticItems syntheticItems = appView.getSyntheticItems();
            if (syntheticItems.hasKindThatMatches(
                method.getHolderType(),
                (kind, naming) -> !kind.isSingleCallerInlineableInPostMethodProcessor(naming))) {
              continue;
            }
          }
          Set<Node> callersWithDeterministicOrder = node.getCallersWithDeterministicOrder();
          ProgramMethod caller = method;
          // We can have recursive methods where the recursive call is the only call site. We do
          // not track callers for these.
          if (!callersWithDeterministicOrder.isEmpty()) {
            assert callersWithDeterministicOrder.size() == 1;
            caller = callersWithDeterministicOrder.iterator().next().getProgramMethod();
          }
          assert !singleCallerMethods.containsKey(reference);
          singleCallerMethods.put(reference, caller.getReference());
          if (methodProcessor.isPostMethodProcessor()
              && appView.getKeepInfo(caller).isReprocessingAllowed(options, caller)) {
            methodProcessor.asPostMethodProcessor().addMethodToProcess(caller);
          }
        } else if (numberOfCallSites > 1 && methodProcessor.isPrimaryMethodProcessor()) {
          multiCallerInlineCandidates.add(reference);
        }
      }
    }

    /**
     * Checks if the given method only has a single call site with the given context.
     *
     * <p>For pinned methods (methods kept through Proguard keep rules) and methods that override a
     * library method this always returns false.
     */
    @Override
    public boolean hasSingleCallSite(ProgramMethod method, ProgramMethod context) {
      return !noSingleCallerInliningInto.contains(context.getReference())
          && context.getReference().isIdenticalTo(singleCallerMethods.get(method.getReference()));
    }

    /**
     * Checks if the given method only has a single call without considering context.
     *
     * <p>For pinned methods (methods kept through Proguard keep rules) and methods that override a
     * library method this always returns false.
     */
    @Override
    public boolean hasSingleCallSite(ProgramMethod method) {
      return singleCallerMethods.containsKey(method.getReference());
    }

    /**
     * Checks if the given method only has two call sites.
     *
     * <p>For pinned methods (methods kept through Proguard keep rules) and methods that override a
     * library method this always returns false.
     */
    @Override
    public boolean isMultiCallerInlineCandidate(ProgramMethod method) {
      return multiCallerInlineCandidates.contains(method.getReference());
    }

    @Override
    public void notifyMethodInlined(ProgramMethod caller, ProgramMethod callee) {
      if (methodProcessor.isPrimaryMethodProcessor()) {
        return;
      }
      assert methodProcessor.isPostMethodProcessor();
      // If the callee is the single caller of another method, then disallow that single caller
      // inlining, since we have now duplicated the call site as a result of this inlining.
      if (singleCallerMethods.containsValue(callee.getReference())) {
        noSingleCallerInliningInto.add(callee.getReference());
      }
    }

    @Override
    public void unsetCallSiteInformation(ProgramMethod method) {
      singleCallerMethods.remove(method.getReference());
      multiCallerInlineCandidates.remove(method.getReference());
    }
  }
}
