// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePolymorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteReceiverValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionBySignature;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.StateCloner;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.UnknownMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class VirtualDispatchMethodArgumentPropagator extends MethodArgumentPropagator {

  class PropagationState {

    // Argument information for virtual methods that must be propagated to all overrides (i.e., this
    // information does not have a lower bound).
    final MethodStateCollectionBySignature active = MethodStateCollectionBySignature.create();

    // Argument information for final virtual methods. The intersection of this and the active
    // tracked collection is empty to guarantee that we do not carry this information downwards.
    // TODO(b/422947619): Also apply pruning to activeUntilLowerBound and inactiveUntilUpperBound.
    final MethodStateCollectionBySignature activeUntilCurrentClass =
        MethodStateCollectionBySignature.create();

    // Argument information for virtual methods that must be propagated to all overrides that are
    // above the given lower bound.
    final Map<DexType, MethodStateCollectionBySignature> activeUntilLowerBound =
        new IdentityHashMap<>();

    // Argument information for virtual methods that is currently inactive, but should be propagated
    // to all overrides below a given upper bound.
    final Map<DynamicTypeWithUpperBound, MethodStateCollectionBySignature> inactiveUntilUpperBound =
        new HashMap<>();

    PropagationState(DexProgramClass clazz) {
      // Join the argument information from each of the super types.
      DexMethodSignatureSet finalMethods = DexMethodSignatureSet.create();
      // TODO(b/422947619): Extend build speed optimization for final methods to package-private
      //  final methods. See PackagePrivateOverridePublicizerTest for an example.
      clazz.forEachProgramVirtualMethodMatching(
          m -> m.isFinal() && !m.getAccessFlags().isPackagePrivate(), finalMethods::add);
      immediateSubtypingInfo.forEachImmediateProgramSuperClass(
          clazz, superclass -> addParentState(clazz, superclass, finalMethods));
    }

    @SuppressWarnings("ReferenceEquality")
    // TODO(b/190154391): This currently copies the state of the superclass into its immediate
    //  given subclass. Instead of copying the state, consider linking the states. This would reduce
    //  memory usage, but would require visiting all transitive (program) super classes for each
    //  subclass.
    private void addParentState(
        DexProgramClass clazz, DexProgramClass superclass, DexMethodSignatureSet finalMethods) {
      PropagationState parentState = propagationStates.get(superclass.asProgramClass());
      assert parentState != null;

      // Add the argument information that must be propagated to all method overrides.
      addActive(parentState.active, finalMethods);

      // Add the argument information that is active until a given lower bound.
      parentState.activeUntilLowerBound.forEach(
          (lowerBound, activeMethodState) -> {
            TypeElement lowerBoundType = lowerBound.toTypeElement(appViewWithLiveness);
            TypeElement currentType = clazz.getType().toTypeElement(appViewWithLiveness);
            if (lowerBoundType.lessThanOrEqual(currentType, appViewWithLiveness)) {
              addActiveUntilCurrentClassOrLowerBound(
                  lowerBound, activeMethodState, clazz, finalMethods);
            } else {
              // No longer active.
            }
          });

      // Add the argument information that is inactive until a given upper bound.
      parentState.inactiveUntilUpperBound.forEach(
          (bounds, inactiveMethodStates) -> {
            ClassTypeElement upperBound = bounds.getDynamicUpperBoundType().asClassType();
            if (!shouldActivateMethodStateGuardedByBounds(upperBound, clazz, superclass)) {
              // Still inactive.
              // TODO(b/190154391): Only carry this information downwards if the upper bound is a
              //  subtype of this class. Otherwise we carry this information to all subtypes,
              //  although clearly the information will never become active.
              addInactiveUntilUpperBound(bounds, inactiveMethodStates, finalMethods);
              return;
            }

            // The upper bound is the current class, thus this inactive information now becomes
            // active.
            if (bounds.hasDynamicLowerBoundType()) {
              // For class methods with sibling interface methods, we can have lower bound type
              // information on the sibling interface method. When this information is propagated
              // down to the common subtype, then there is no need to propagate the information
              // any further, since the common subtype is already below the lower bound.
              //
              // Note that this does not imply that the information stored on the sibling
              // interface method is not applied. The information is propagated to the class
              // method that implements the interface method below.
              ClassTypeElement lowerBound = bounds.getDynamicLowerBoundType();
              TypeElement currentType = clazz.getType().toTypeElement(appViewWithLiveness);
              if (lowerBound.lessThanOrEqual(currentType, appViewWithLiveness)) {
                DexType activeUntilLowerBoundType =
                    lowerBound.toDexType(appViewWithLiveness.dexItemFactory());
                addActiveUntilCurrentClassOrLowerBound(
                    activeUntilLowerBoundType, inactiveMethodStates, clazz, finalMethods);
              } else {
                return;
              }
            } else {
              addActive(inactiveMethodStates, finalMethods);
            }

            inactiveMethodStates.forEach(
                (signature, methodState) -> {
                  SingleResolutionResult<?> resolutionResult =
                      appViewWithLiveness
                          .appInfo()
                          .resolveMethodOnLegacy(clazz, signature)
                          .asSingleResolution();

                  // Find the first virtual method in the super class hierarchy.
                  while (resolutionResult != null
                      && resolutionResult.getResolvedMethod().belongsToDirectPool()) {
                    resolutionResult =
                        appViewWithLiveness
                            .appInfo()
                            .resolveMethodOnClassLegacy(
                                resolutionResult.getResolvedHolder().getSuperType(), signature)
                            .asSingleResolution();
                  }

                  // Propagate the argument information to the method on the super class.
                  if (resolutionResult != null
                      && resolutionResult.getResolvedHolder().isProgramClass()
                      && resolutionResult.getResolvedHolder() != clazz
                      && resolutionResult.getResolvedMethod().hasCode()) {
                    DexProgramClass resolvedHolder =
                        resolutionResult.getResolvedHolder().asProgramClass();
                    PropagationState propagationState = propagationStates.get(resolvedHolder);
                    propagationState.addActiveUntilCurrentClass(
                        resolutionResult.getResolvedProgramMethod(), methodState);
                  }
                });
          });
    }

    private void addActive(ProgramMethod method, MethodState methodState) {
      internalAddActive(
          method.getMethodSignature(),
          methodState,
          method.getAccessFlags().isFinal() && !method.getAccessFlags().isPackagePrivate());
    }

    private void addActive(
        MethodStateCollectionBySignature other, DexMethodSignatureSet finalMethods) {
      other.forEach(
          (method, methodState) ->
              internalAddActive(method, methodState, finalMethods.contains(method)));
    }

    private void internalAddActive(
        DexMethodSignature method, MethodState methodState, boolean isFinal) {
      if (isFinal) {
        activeUntilCurrentClass.addMethodState(appViewWithLiveness, method, methodState);
      } else {
        active.addMethodState(appViewWithLiveness, method, methodState);
      }
    }

    private void setActive(ProgramMethod method, MethodState methodState) {
      if (method.getAccessFlags().isFinal() && !method.getAccessFlags().isPackagePrivate()) {
        activeUntilCurrentClass.set(method, methodState);
      } else {
        active.set(method, methodState);
      }
    }

    private void addActiveUntilCurrentClass(ProgramMethod method, MethodState methodState) {
      activeUntilCurrentClass.addMethodState(appViewWithLiveness, method, methodState);
    }

    private void addActiveUntilCurrentClassOrLowerBound(
        DexType lowerBound, ProgramMethod method, MethodState methodState) {
      boolean isFinal =
          method.getAccessFlags().isFinal() && !method.getAccessFlags().isPackagePrivate();
      if (isFinal || lowerBound.isIdenticalTo(method.getHolderType())) {
        addActiveUntilCurrentClass(method, methodState);
      } else {
        addActiveUntilLowerBound(lowerBound, method, methodState);
      }
    }

    private void addActiveUntilCurrentClassOrLowerBound(
        DexType lowerBound,
        MethodStateCollectionBySignature otherMethodStates,
        DexProgramClass currentClass,
        DexMethodSignatureSet finalMethods) {
      if (lowerBound.isIdenticalTo(currentClass.getType())) {
        activeUntilCurrentClass.addMethodStates(appViewWithLiveness, otherMethodStates);
      } else {
        otherMethodStates.forEach(
            (method, methodState) -> {
              if (finalMethods.contains(method)) {
                activeUntilCurrentClass.addMethodState(appViewWithLiveness, method, methodState);
              } else {
                activeUntilLowerBound
                    .computeIfAbsent(
                        lowerBound, ignoreKey(MethodStateCollectionBySignature::create))
                    .addMethodState(appViewWithLiveness, method, methodState);
              }
            });
      }
    }

    private void addActiveUntilLowerBound(
        DexType lowerBound, ProgramMethod method, MethodState methodState) {
      activeUntilLowerBound
          .computeIfAbsent(lowerBound, ignoreKey(MethodStateCollectionBySignature::create))
          .addMethodState(appViewWithLiveness, method, methodState);
    }

    private void addInactiveUntilUpperBound(
        DynamicTypeWithUpperBound upperBound, ProgramMethod method, MethodState methodState) {
      boolean isFinal =
          method.getAccessFlags().isFinal() && !method.getAccessFlags().isPackagePrivate();
      if (!isFinal) {
        inactiveUntilUpperBound
            .computeIfAbsent(upperBound, ignoreKey(MethodStateCollectionBySignature::create))
            .addMethodState(appViewWithLiveness, method, methodState);
      }
    }

    private void addInactiveUntilUpperBound(
        DynamicTypeWithUpperBound upperBound,
        MethodStateCollectionBySignature otherMethodStates,
        DexMethodSignatureSet finalMethods) {
      otherMethodStates.forEach(
          (method, methodState) -> {
            if (!finalMethods.contains(method)) {
              inactiveUntilUpperBound
                  .computeIfAbsent(upperBound, ignoreKey(MethodStateCollectionBySignature::create))
                  .addMethodStates(appViewWithLiveness, otherMethodStates);
            }
          });
    }

    private MethodState computeMethodStateForPolymorphicMethod(ProgramMethod method) {
      assert method.getDefinition().isNonPrivateVirtualMethod();
      MethodState methodState = active.get(method).mutableCopy();
      DexMethodSignature methodSignature = method.getMethodSignature();
      methodState =
          methodState.mutableJoin(
              appViewWithLiveness,
              methodSignature,
              activeUntilCurrentClass.get(method),
              StateCloner.getCloner());
      if (!activeUntilLowerBound.isEmpty()) {
        for (MethodStateCollectionBySignature methodStates : activeUntilLowerBound.values()) {
          methodState =
              methodState.mutableJoin(
                  appViewWithLiveness,
                  methodSignature,
                  methodStates.get(method),
                  StateCloner.getCloner());
        }
      }
      if (methodState.isMonomorphic()) {
        ConcreteMonomorphicMethodState monomorphicMethodState = methodState.asMonomorphic();
        ValueState receiverState = monomorphicMethodState.getParameterState(0);
        if (receiverState.isReceiverState()) {
          ConcreteReceiverValueState concreteReceiverState = receiverState.asReceiverState();
          DynamicType dynamicType = concreteReceiverState.getDynamicType();
          DynamicType refinedDynamicType = computeRefinedReceiverDynamicType(method, dynamicType);
          if (!refinedDynamicType.equals(dynamicType)) {
            monomorphicMethodState.setParameterState(
                0,
                refinedDynamicType.isNotNullType()
                    ? ValueState.unknown()
                    : new ConcreteReceiverValueState(
                        refinedDynamicType, concreteReceiverState.copyInFlow()));
          }
        } else {
          assert receiverState.isBottom() || receiverState.isUnknown();
        }
      }
      return methodState;
    }

    private DynamicType computeRefinedReceiverDynamicType(
        ProgramMethod method, DynamicType dynamicType) {
      if (!dynamicType.isDynamicTypeWithUpperBound()) {
        return dynamicType;
      }
      DynamicTypeWithUpperBound dynamicTypeWithUpperBound =
          dynamicType.asDynamicTypeWithUpperBound();
      TypeElement dynamicUpperBoundType = dynamicTypeWithUpperBound.getDynamicUpperBoundType();
      TypeElement staticUpperBoundType =
          method.getHolderType().toTypeElement(appViewWithLiveness, definitelyNotNull());
      if (dynamicUpperBoundType.lessThanOrEqualUpToNullability(
          staticUpperBoundType, appViewWithLiveness)) {
        DynamicType newDynamicType = dynamicType.withNullability(definitelyNotNull());
        assert newDynamicType.equals(dynamicType)
            || !dynamicType.getNullability().isDefinitelyNotNull();
        return newDynamicType;
      }
      ClassTypeElement dynamicLowerBoundType = dynamicTypeWithUpperBound.getDynamicLowerBoundType();
      if (dynamicLowerBoundType == null) {
        return DynamicType.definitelyNotNull();
      }
      assert dynamicLowerBoundType.lessThanOrEqualUpToNullability(
          staticUpperBoundType, appViewWithLiveness);
      if (dynamicLowerBoundType.equalUpToNullability(staticUpperBoundType)) {
        return DynamicType.createExact(dynamicLowerBoundType.asDefinitelyNotNull());
      }
      return DynamicType.create(
          appViewWithLiveness, staticUpperBoundType, dynamicLowerBoundType.asDefinitelyNotNull());
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean shouldActivateMethodStateGuardedByBounds(
        ClassTypeElement upperBound, DexProgramClass currentClass, DexProgramClass superClass) {
      ClassTypeElement classType =
          TypeElement.fromDexType(currentClass.getType(), maybeNull(), appViewWithLiveness)
              .asClassType();
      // When propagating argument information for interface methods downwards from an interface to
      // a non-interface we need to account for the parent classes of the current class.
      if (superClass.isInterface()
          && !currentClass.isInterface()
          && currentClass.getSuperType() != appViewWithLiveness.dexItemFactory().objectType) {
        return classType.lessThanOrEqualUpToNullability(upperBound, appViewWithLiveness);
      }
      // If the upper bound does not have any interfaces we simply activate the method state when
      // meeting the upper bound class type in the downwards traversal over the class hierarchy.
      if (classType.getInterfaces().isEmpty()) {
        return classType.equalUpToNullability(upperBound);
      }
      // If the upper bound has interfaces, we check if the current class is a subtype of *both* the
      // upper bound class type and the upper bound interface types.
      return classType.lessThanOrEqualUpToNullability(upperBound, appViewWithLiveness);
    }

    boolean verifyActiveUntilLowerBoundRelevance(DexProgramClass clazz) {
      TypeElement currentType = clazz.getType().toTypeElement(appViewWithLiveness);
      for (DexType lowerBound : activeUntilLowerBound.keySet()) {
        TypeElement lowerBoundType = lowerBound.toTypeElement(appViewWithLiveness);
        assert lowerBoundType.lessThanOrEqual(currentType, appViewWithLiveness);
      }
      return true;
    }
  }

  final AppView<AppInfoWithLiveness> appViewWithLiveness;
  final Timing timing;

  // For each class, stores the argument information for each virtual method on this class and all
  // direct and indirect super classes.
  //
  // This data structure is populated during a top-down traversal over the class hierarchy, such
  // that entries in the map can be removed when the top-down traversal has visited all subtypes of
  // a given node.
  final Map<DexProgramClass, PropagationState> propagationStates = new IdentityHashMap<>();

  public VirtualDispatchMethodArgumentPropagator(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollectionByReference methodStates,
      Timing timing) {
    super(appView, immediateSubtypingInfo, methodStates);
    this.appViewWithLiveness = appView;
    this.timing = timing;
  }

  @Override
  public void run(Collection<DexProgramClass> stronglyConnectedComponent) {
    super.run(stronglyConnectedComponent);
    assert verifyAllClassesFinished(stronglyConnectedComponent);
    assert verifyStatePruned();
  }

  @Override
  public void visit(DexProgramClass clazz) {
    assert !propagationStates.containsKey(clazz);
    timing.begin("Compute propagation state");
    computePropagationState(clazz);
    timing.end();
  }

  private void computePropagationState(DexProgramClass clazz) {
    timing.begin("Add parent states");
    PropagationState propagationState = new PropagationState(clazz);
    timing.end();

    // Join the argument information from the methods of the current class.
    clazz.forEachProgramVirtualMethod(
        method -> {
          MethodState methodState = methodStates.get(method);
          if (methodState.isBottom()) {
            return;
          }

          // TODO(b/190154391): Add an unknown polymorphic method state, such that we can
          //  distinguish monomorphic unknown method states from polymorphic unknown method states.
          //  We only need to propagate polymorphic unknown method states here.
          if (methodState.isUnknown()) {
            propagationState.setActive(method, UnknownMethodState.get());
            return;
          }

          ConcreteMethodState concreteMethodState = methodState.asConcrete();
          if (concreteMethodState.isMonomorphic()) {
            // No need to propagate information for methods that do not override other methods and
            // are not themselves overridden.
            return;
          }

          ConcretePolymorphicMethodState polymorphicMethodState =
              concreteMethodState.asPolymorphic();
          timing.begin("Visit polymorphic method state");
          polymorphicMethodState.forEach(
              (bounds, methodStateForBounds) -> {
                if (bounds.isUnknown()) {
                  propagationState.addActive(method, methodStateForBounds);
                } else {
                  // TODO(b/190154391): Verify that the bounds are not trivial according to the
                  //  static receiver type.
                  ClassTypeElement upperBound = bounds.getDynamicUpperBoundType().asClassType();
                  if (isUpperBoundSatisfied(upperBound, clazz)) {
                    if (bounds.hasDynamicLowerBoundType()) {
                      // TODO(b/190154391): Verify that the lower bound is a subtype of the current
                      //  class.
                      ClassTypeElement lowerBound = bounds.getDynamicLowerBoundType();
                      DexType activeUntilLowerBoundType =
                          lowerBound.toDexType(appViewWithLiveness.dexItemFactory());
                      assert !bounds.isExactClassType()
                          || activeUntilLowerBoundType.isIdenticalTo(clazz.getType());
                      propagationState.addActiveUntilCurrentClassOrLowerBound(
                          activeUntilLowerBoundType, method, methodStateForBounds);
                    } else {
                      propagationState.addActive(method, methodStateForBounds);
                    }
                  } else {
                    assert !clazz
                        .getType()
                        .toTypeElement(appViewWithLiveness)
                        .lessThanOrEqualUpToNullability(upperBound, appViewWithLiveness);
                    propagationState.addInactiveUntilUpperBound(
                        bounds, method, methodStateForBounds);
                  }
                }
              });
          timing.end();
        });

    assert propagationState.verifyActiveUntilLowerBoundRelevance(clazz);
    propagationStates.put(clazz, propagationState);
  }

  private boolean isUpperBoundSatisfied(ClassTypeElement upperBound, DexProgramClass currentClass) {
    DexType upperBoundType = upperBound.toDexType(appViewWithLiveness.dexItemFactory());
    DexProgramClass upperBoundClass =
        asProgramClassOrNull(appViewWithLiveness.definitionFor(upperBoundType));
    if (upperBoundClass == null) {
      // We should generally never have a dynamic receiver upper bound for a program method which is
      // not a program class. However, since the program may not type change or there could be
      // missing classes, we still need to cover this case. In the rare cases where this happens, we
      // conservatively consider the upper bound to be satisfied.
      return true;
    }
    return upperBoundClass == currentClass;
  }

  private void computeFinalMethodStates(DexProgramClass clazz, PropagationState propagationState) {
    clazz.forEachProgramVirtualMethod(method -> computeFinalMethodState(method, propagationState));
  }

  private void computeFinalMethodState(ProgramMethod method, PropagationState propagationState) {
    if (!method.getDefinition().hasCode()) {
      methodStates.remove(method);
      return;
    }

    MethodState methodState = methodStates.get(method);
    if (methodState.isMonomorphic() || methodState.isUnknown()) {
      return;
    }

    assert methodState.isBottom() || methodState.isPolymorphic();

    // This is a polymorphic method and we need to compute the method state to account for dynamic
    // dispatch.
    methodState = propagationState.computeMethodStateForPolymorphicMethod(method);
    assert !methodState.isConcrete() || methodState.asConcrete().isMonomorphic();
    methodStates.set(method, methodState);
  }

  @Override
  public void prune(DexProgramClass clazz) {
    timing.begin("Compute final method states");
    PropagationState propagationState = propagationStates.remove(clazz);
    computeFinalMethodStates(clazz, propagationState);
    timing.end();
  }

  private boolean verifyAllClassesFinished(Collection<DexProgramClass> stronglyConnectedComponent) {
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      assert isClassFinished(clazz);
    }
    return true;
  }

  private boolean verifyStatePruned() {
    assert propagationStates.isEmpty();
    return true;
  }
}
