// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.accessmodification;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

class AccessModifierTraversal extends DepthFirstTopDownClassHierarchyTraversal {

  private final AccessModifier accessModifier;
  private final AccessModifierNamingState namingState;

  private final Map<DexProgramClass, TraversalState> states = new IdentityHashMap<>();

  AccessModifierTraversal(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      AccessModifier accessModifier,
      AccessModifierNamingState namingState) {
    super(appView, immediateSubtypingInfo);
    this.accessModifier = accessModifier;
    this.namingState = namingState;
  }

  /** Predicate that specifies which program classes the depth-first traversal should start from. */
  @Override
  public boolean isRoot(DexProgramClass clazz) {
    return Iterables.all(
        clazz.allImmediateSupertypes(),
        supertype -> asProgramClassOrNull(appView.definitionFor(supertype)) == null);
  }

  /** Called when {@param clazz} is visited for the first time during the downwards traversal. */
  @Override
  public void visit(DexProgramClass clazz) {
    // TODO(b/279126633): Store a top down traversal state for the current class, which contains the
    //  protected and public method signatures when traversing downwards to enable publicizing of
    //  package private methods with illegal overrides.
    states.put(clazz, TopDownTraversalState.empty());
  }

  /** Called during backtracking when all subclasses of {@param clazz} have been processed. */
  @Override
  public void prune(DexProgramClass clazz) {
    BottomUpTraversalState state = getOrCreateBottomUpTraversalState(clazz);

    // Apply access modification to the class and its members.
    accessModifier.processClass(clazz, namingState, state);

    // Add the methods of the current class.
    clazz.forEachProgramVirtualMethod(state::addMethod);

    // Store the bottom up traversal state for the current class.
    if (!state.isEmpty()) {
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(
          clazz,
          superClass -> {
            BottomUpTraversalState superState = getOrCreateBottomUpTraversalState(superClass);
            superState.add(state);
          });
    }

    // Done processing the current class and all subclasses.
    states.remove(clazz);
  }

  private BottomUpTraversalState getOrCreateBottomUpTraversalState(DexProgramClass clazz) {
    TraversalState traversalState = states.get(clazz);
    if (traversalState == null || traversalState.isTopDownTraversalState()) {
      KeepClassInfo keepInfo = appView.getKeepInfo(clazz);
      InternalOptions options = appView.options();
      BottomUpTraversalState newState =
          new BottomUpTraversalState(
              !keepInfo.isMinificationAllowed(options) && !keepInfo.isShrinkingAllowed(options));
      states.put(clazz, newState);
      return newState;
    }
    assert traversalState.isBottomUpTraversalState();
    return traversalState.asBottomUpTraversalState();
  }

  private abstract static class TraversalState {

    boolean isBottomUpTraversalState() {
      return false;
    }

    BottomUpTraversalState asBottomUpTraversalState() {
      return null;
    }

    boolean isTopDownTraversalState() {
      return false;
    }

    TopDownTraversalState asTopDownTraversalState() {
      return null;
    }
  }

  // TODO(b/279126633): Collect the protected and public method signatures when traversing downwards
  //  to enable publicizing of package private methods with illegal overrides.
  private static class TopDownTraversalState extends TraversalState {

    private static final TopDownTraversalState EMPTY = new TopDownTraversalState();

    static TopDownTraversalState empty() {
      return EMPTY;
    }

    @Override
    boolean isTopDownTraversalState() {
      return true;
    }

    @Override
    TopDownTraversalState asTopDownTraversalState() {
      return this;
    }
  }

  static class BottomUpTraversalState extends TraversalState {

    private static final BottomUpTraversalState EMPTY =
        new BottomUpTraversalState(DexMethodSignatureMap.empty());

    boolean isKeptOrHasKeptSubclass;

    // The set of non-private virtual methods below the current class.
    DexMethodSignatureMap<Set<String>> nonPrivateVirtualMethods;

    private BottomUpTraversalState(boolean isKept) {
      this(DexMethodSignatureMap.create());
      this.isKeptOrHasKeptSubclass = isKept;
    }

    private BottomUpTraversalState(DexMethodSignatureMap<Set<String>> packagePrivateMethods) {
      this.nonPrivateVirtualMethods = packagePrivateMethods;
    }

    static BottomUpTraversalState asBottomUpTraversalStateOrNull(TraversalState traversalState) {
      return (BottomUpTraversalState) traversalState;
    }

    static BottomUpTraversalState empty() {
      return EMPTY;
    }

    @Override
    boolean isBottomUpTraversalState() {
      return true;
    }

    @Override
    BottomUpTraversalState asBottomUpTraversalState() {
      return this;
    }

    void add(BottomUpTraversalState backtrackingState) {
      isKeptOrHasKeptSubclass |= backtrackingState.isKeptOrHasKeptSubclass;
      backtrackingState.nonPrivateVirtualMethods.forEach(
          (methodSignature, packageDescriptors) ->
              this.nonPrivateVirtualMethods
                  .computeIfAbsent(methodSignature, ignoreKey(HashSet::new))
                  .addAll(packageDescriptors));
    }

    void addMethod(ProgramMethod method) {
      assert method.getDefinition().belongsToVirtualPool();
      nonPrivateVirtualMethods
          .computeIfAbsent(method.getMethodSignature(), ignoreKey(Sets::newIdentityHashSet))
          .add(method.getHolderType().getPackageDescriptor());
    }

    boolean hasIllegalOverrideOfPackagePrivateMethod(ProgramMethod method) {
      assert method.getAccessFlags().isPackagePrivate();
      String methodPackageDescriptor = method.getHolderType().getPackageDescriptor();
      return Iterables.any(
          nonPrivateVirtualMethods.getOrDefault(
              method.getMethodSignature(), Collections.emptySet()),
          methodOverridePackageDescriptor ->
              !methodOverridePackageDescriptor.equals(methodPackageDescriptor));
    }

    boolean isEmpty() {
      return nonPrivateVirtualMethods.isEmpty();
    }

    void setIsKeptOrHasKeptSubclass() {
      isKeptOrHasKeptSubclass = true;
    }
  }
}
