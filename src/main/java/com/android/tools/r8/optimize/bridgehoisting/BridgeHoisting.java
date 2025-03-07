// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.bridgehoisting;


import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.bridge.VirtualBridgeInfo;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * An optimization pass that hoists bridges upwards with the purpose of sharing redundant bridge
 * methods.
 *
 * <p>Example: <code>
 *   class A {
 *     void m() { ... }
 *   }
 *   class B1 extends A {
 *     void bridge() { m(); }
 *   }
 *   class B2 extends A {
 *     void bridge() { m(); }
 *   }
 * </code> Is transformed into: <code>
 *   class A {
 *     void m() { ... }
 *     void bridge() { m(); }
 *   }
 *   class B1 extends A {}
 *   class B2 extends A {}
 * </code>
 */
public class BridgeHoisting {

  private static final OptimizationFeedbackSimple feedback =
      OptimizationFeedbackSimple.getInstance();

  private final AppView<AppInfoWithLiveness> appView;

  // Structure that keeps track of the changes for construction of the Proguard map and
  // AppInfoWithLiveness maintenance.
  private final BridgeHoistingResult result;

  public BridgeHoisting(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.result = new BridgeHoistingResult(appView);
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Bridge hoisting");
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    BottomUpClassHierarchyTraversal.forProgramClasses(appView, immediateSubtypingInfo)
        .excludeInterfaces()
        .visit(appView.appInfo().classes(), clazz -> processClass(clazz, immediateSubtypingInfo));
    if (!result.isEmpty()) {
      BridgeHoistingLens lens = result.buildLens();
      appView.rewriteWithLens(lens, executorService, timing);
    }

    appView.notifyOptimizationFinishedForTesting();
    timing.end();
  }

  private void processClass(
      DexProgramClass clazz, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    List<DexProgramClass> subclasses =
        ListUtils.sort(
            immediateSubtypingInfo.getSubclasses(clazz), Comparator.comparing(DexClass::getType));
    List<DexProgramClass> eligibleSubclasses = new ArrayList<>(subclasses.size());
    for (DexProgramClass subclass : subclasses) {
      if (appView.testing().isEligibleForBridgeHoisting.test(subclass)) {
        eligibleSubclasses.add(subclass);
      }
    }
    for (ProgramMethod candidate : getCandidatesForHoisting(eligibleSubclasses)) {
      hoistBridgeIfPossible(candidate, clazz, eligibleSubclasses);
    }
  }

  private Collection<ProgramMethod> getCandidatesForHoisting(List<DexProgramClass> subclasses) {
    Equivalence<DexMethod> equivalence = MethodSignatureEquivalence.get();
    Map<Wrapper<DexMethod>, ProgramMethod> candidates = new LinkedHashMap<>();
    for (DexProgramClass subclass : subclasses) {
      for (ProgramMethod method : subclass.virtualProgramMethods()) {
        if (appView.getKeepInfo(method).isPinned(appView.options())) {
          continue;
        }
        BridgeInfo bridgeInfo = method.getOptimizationInfo().getBridgeInfo();
        if (bridgeInfo != null && bridgeInfo.isVirtualBridgeInfo()) {
          candidates.put(equivalence.wrap(method.getReference()), method);
        }
      }
    }
    return candidates.values();
  }

  private void hoistBridgeIfPossible(
      ProgramMethod method, DexProgramClass clazz, List<DexProgramClass> subclasses) {
    // If the method is defined on the parent class, we cannot hoist the bridge.
    // TODO(b/153147967): If the declared method is abstract, we could replace it by the bridge.
    //  Add a test.
    DexMethod methodReference = method.getReference();
    if (clazz.lookupProgramMethod(methodReference) != null) {
      return;
    }

    // Bail out if the bridge is also declared in the parent class. In that case, hoisting would
    // change the behavior of calling the bridge on an instance of the parent class.
    if (clazz.hasSuperType()) {
      MethodResolutionResult res =
          appView.appInfo().resolveMethodOnClass(clazz.getSuperType(), methodReference);
      if (res.isSingleResolution()) {
        if (!res.getResolvedMethod().isAbstract()) {
          return;
        }
      } else if (res.isMultiMethodResolutionResult()) {
        return;
      }
    }

    // Go through each of the subclasses and find the bridges that can be hoisted. The bridge holder
    // classes are stored in buckets grouped by the behavior of the body of the bridge (which is
    // implicitly defined by the signature of the invoke-virtual instruction).
    Map<Wrapper<DexMethod>, List<DexProgramClass>> eligibleVirtualInvokeBridges = new HashMap<>();
    for (DexProgramClass subclass : subclasses) {
      DexEncodedMethod definition = subclass.lookupVirtualMethod(methodReference);
      if (definition == null) {
        DexEncodedMethod resolutionTarget =
            appView
                .appInfo()
                .resolveMethodOnClassLegacy(subclass, methodReference)
                .getSingleTarget();
        if (resolutionTarget == null || resolutionTarget.isAbstract()) {
          // The fact that this class does not declare the bridge (or the bridge is abstract) should
          // not prevent us from hoisting the bridge.
          //
          // Strictly speaking, there could be an invoke instruction that targets the bridge on this
          // subclass and fails with an AbstractMethodError or a NoSuchMethodError in the input
          // program. After hoisting the bridge to the superclass such an instruction would no
          // longer fail with an error in the generated program.
          //
          // If this ever turns out be an issue, it would be possible to track if there is an invoke
          // instruction targeting the bridge on this subclass that fails in the Enqueuer, but this
          // should never be the case in practice.
          continue;
        }

        // Hoisting would change the program behavior.
        return;
      }

      BridgeInfo currentBridgeInfo = definition.getOptimizationInfo().getBridgeInfo();
      if (currentBridgeInfo == null || !currentBridgeInfo.isVirtualBridgeInfo()) {
        // This is not a virtual bridge, so the method needs to remain on the subclass.
        continue;
      }

      VirtualBridgeInfo currentVirtualBridgeInfo = currentBridgeInfo.asVirtualBridgeInfo();
      DexMethod invokedMethod = currentVirtualBridgeInfo.getInvokedMethod();

      if (!clazz.getType().isSamePackage(subclass.getType())) {
        DexEncodedMethod resolvedMethod =
            appView.appInfo().resolveMethodOnClass(clazz, invokedMethod).getSingleTarget();
        if (resolvedMethod == null || resolvedMethod.getAccessFlags().isPackagePrivate()) {
          // After hoisting this bridge would now dispatch to another method, namely the package
          // private method in the parent class.
          continue;
        }
      }

      Wrapper<DexMethod> wrapper = MethodSignatureEquivalence.get().wrap(invokedMethod);
      eligibleVirtualInvokeBridges
          .computeIfAbsent(wrapper, ignore -> new ArrayList<>())
          .add(subclass);
    }

    // Check if any bridges may be eligible for hoisting.
    if (eligibleVirtualInvokeBridges.isEmpty()) {
      return;
    }

    Entry<Wrapper<DexMethod>, List<DexProgramClass>> mostFrequentBridge =
        findMostFrequentBridge(eligibleVirtualInvokeBridges);
    assert mostFrequentBridge != null;
    DexMethod invokedMethod = mostFrequentBridge.getKey().get();
    List<DexProgramClass> eligibleSubclasses = mostFrequentBridge.getValue();

    // Choose one of the bridge definitions as the one that we will be moving to the superclass.
    List<ProgramMethod> eligibleBridgeMethods =
        getBridgesEligibleForHoisting(eligibleSubclasses, methodReference);
    ProgramMethod representative = eligibleBridgeMethods.iterator().next();

    // Guard against accessibility issues.
    if (mayBecomeInaccessibleAfterHoisting(clazz, eligibleBridgeMethods, representative)) {
      return;
    }

    // Rewrite the invoke-virtual instruction to target the virtual method on the new holder class.
    // Otherwise the code might not type check.
    DexMethod methodToInvoke =
        appView.dexItemFactory().createMethod(clazz.type, invokedMethod.proto, invokedMethod.name);

    // The targeted method must be present on the new holder class for this to be feasible.
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnClassLegacy(clazz, methodToInvoke);
    if (!resolutionResult.isSingleResolution()) {
      return;
    }

    // Now update the code of the bridge method chosen as representative.
    representative
        .setCode(createCodeForVirtualBridge(representative, methodToInvoke), appView);
    feedback.setBridgeInfo(representative, new VirtualBridgeInfo(methodToInvoke));

    // Move the bridge method to the super class, and record this in the graph lens.
    DexMethod newMethodReference = methodReference.withHolder(clazz, appView.dexItemFactory());
    DexEncodedMethod newMethod =
        representative
            .getDefinition()
            .toTypeSubstitutedMethodAsInlining(newMethodReference, appView.dexItemFactory());
    if (newMethod.getAccessFlags().isFinal()) {
      newMethod.getAccessFlags().demoteFromFinal();
    }
    clazz.addVirtualMethod(newMethod);
    result.move(
        Iterables.transform(eligibleBridgeMethods, DexClassAndMember::getReference),
        newMethodReference,
        representative.getReference());

    // Remove all of the bridges in the eligible subclasses.
    for (DexProgramClass subclass : eligibleSubclasses) {
      DexEncodedMethod removed = subclass.removeMethod(methodReference);
      assert removed != null;
    }
  }

  private static Entry<Wrapper<DexMethod>, List<DexProgramClass>> findMostFrequentBridge(
      Map<Wrapper<DexMethod>, List<DexProgramClass>> eligibleVirtualInvokeBridges) {
    Entry<Wrapper<DexMethod>, List<DexProgramClass>> result = null;
    for (Entry<Wrapper<DexMethod>, List<DexProgramClass>> candidate :
        eligibleVirtualInvokeBridges.entrySet()) {
      List<DexProgramClass> eligibleSubclassesCandidate = candidate.getValue();
      if (result == null || eligibleSubclassesCandidate.size() > result.getValue().size()) {
        result = candidate;
      }
    }
    return result;
  }

  private List<ProgramMethod> getBridgesEligibleForHoisting(
      Iterable<DexProgramClass> subclasses, DexMethod reference) {
    List<ProgramMethod> result = new ArrayList<>();
    for (DexProgramClass subclass : subclasses) {
      ProgramMethod method = subclass.lookupProgramMethod(reference);
      if (method != null) {
        result.add(method);
      }
    }
    assert !result.isEmpty();
    return result;
  }

  private boolean mayBecomeInaccessibleAfterHoisting(
      DexProgramClass clazz,
      List<ProgramMethod> eligibleBridgeMethods,
      ProgramMethod representative) {
    int representativeVisibility = representative.getAccessFlags().getVisibilityOrdinal();
    for (ProgramMethod eligibleBridgeMethod : eligibleBridgeMethods) {
      if (eligibleBridgeMethod.getAccessFlags().getVisibilityOrdinal()
          != representativeVisibility) {
        return true;
      }
      if (!clazz.getType().isSamePackage(eligibleBridgeMethod.getHolderType())
          && !eligibleBridgeMethod.getDefinition().isPublic()) {
        return true;
      }
    }
    return false;
  }

  private LirCode<Integer> createCodeForVirtualBridge(
      ProgramMethod representative, DexMethod methodToInvoke) {
    LirCode<Integer> code = representative.getDefinition().getCode().asLirCode();
    return code.newCodeWithRewrittenConstantPool(
        item -> {
          if (item instanceof DexMethod) {
           assert methodToInvoke.match((DexMethod) item);
           return methodToInvoke;
          }
          return item;
        });
  }
}
