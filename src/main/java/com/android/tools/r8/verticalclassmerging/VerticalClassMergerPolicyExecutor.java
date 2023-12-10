// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.getApiReferenceLevelForMerging;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.features.FeatureSplitBoundaryOptimizationUtils;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO(b/315252934): Parallelize policy execution over connected program components.
public class VerticalClassMergerPolicyExecutor {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final MainDexInfo mainDexInfo;
  private final Set<DexProgramClass> pinnedClasses;
  private final VerticallyMergedClasses.Builder verticallyMergedClassesBuilder;

  VerticalClassMergerPolicyExecutor(
      AppView<AppInfoWithLiveness> appView,
      Set<DexProgramClass> pinnedClasses,
      VerticallyMergedClasses.Builder verticallyMergedClassesInComponentBuilder) {
    this.appView = appView;
    this.options = appView.options();
    this.mainDexInfo = appView.appInfo().getMainDexInfo();
    this.pinnedClasses = pinnedClasses;
    this.verticallyMergedClassesBuilder = verticallyMergedClassesInComponentBuilder;
  }

  Set<DexProgramClass> run(
      Set<DexProgramClass> connectedComponent,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    Set<DexProgramClass> mergeCandidates = Sets.newIdentityHashSet();
    for (DexProgramClass sourceClass : connectedComponent) {
      List<DexProgramClass> subclasses = immediateSubtypingInfo.getSubclasses(sourceClass);
      if (subclasses.size() != 1) {
        continue;
      }
      DexProgramClass targetClass = ListUtils.first(subclasses);
      if (!isMergeCandidate(sourceClass, targetClass)) {
        continue;
      }
      if (!isStillMergeCandidate(sourceClass, targetClass)) {
        continue;
      }
      if (mergeMayLeadToIllegalAccesses(sourceClass, targetClass)) {
        continue;
      }
      mergeCandidates.add(sourceClass);
    }
    return mergeCandidates;
  }

  // Returns true if [clazz] is a merge candidate. Note that the result of the checks in this
  // method do not change in response to any class merges.
  private boolean isMergeCandidate(DexProgramClass sourceClass, DexProgramClass targetClass) {
    assert targetClass != null;
    ObjectAllocationInfoCollection allocationInfo =
        appView.appInfo().getObjectAllocationInfoCollection();
    if (allocationInfo.isInstantiatedDirectly(sourceClass)
        || allocationInfo.isInterfaceWithUnknownSubtypeHierarchy(sourceClass)
        || allocationInfo.isImmediateInterfaceOfInstantiatedLambda(sourceClass)
        || !appView.getKeepInfo(sourceClass).isVerticalClassMergingAllowed(options)
        || pinnedClasses.contains(sourceClass)) {
      return false;
    }

    assert sourceClass
        .traverseProgramMembers(
            member -> {
              assert !appView.getKeepInfo(member).isPinned(options);
              return TraversalContinuation.doContinue();
            })
        .shouldContinue();

    if (!FeatureSplitBoundaryOptimizationUtils.isSafeForVerticalClassMerging(
        sourceClass, targetClass, appView)) {
      return false;
    }
    if (appView.appServices().allServiceTypes().contains(sourceClass.getType())
        && appView.getKeepInfo(targetClass).isPinned(options)) {
      return false;
    }
    if (sourceClass.isAnnotation()) {
      return false;
    }
    if (!sourceClass.isInterface()
        && targetClass.isSerializable(appView)
        && !appView.appInfo().isSerializable(sourceClass.getType())) {
      // https://docs.oracle.com/javase/8/docs/platform/serialization/spec/serial-arch.html
      //   1.10 The Serializable Interface
      //   ...
      //   A Serializable class must do the following:
      //   ...
      //     * Have access to the no-arg constructor of its first non-serializable superclass
      return false;
    }

    // If there is a constructor in the target, make sure that all source constructors can be
    // inlined.
    if (!Iterables.isEmpty(targetClass.programInstanceInitializers())) {
      TraversalContinuation<?, ?> result =
          sourceClass.traverseProgramInstanceInitializers(
              method -> TraversalContinuation.breakIf(disallowInlining(method, targetClass)));
      if (result.shouldBreak()) {
        return false;
      }
    }
    if (sourceClass.hasEnclosingMethodAttribute() || !sourceClass.getInnerClasses().isEmpty()) {
      return false;
    }
    // We abort class merging when merging across nests or from a nest to non-nest.
    // Without nest this checks null == null.
    if (ObjectUtils.notIdentical(targetClass.getNestHost(), sourceClass.getNestHost())) {
      return false;
    }

    // If there is an invoke-special to a default interface method and we are not merging into an
    // interface, then abort, since invoke-special to a virtual class method requires desugaring.
    if (sourceClass.isInterface() && !targetClass.isInterface()) {
      TraversalContinuation<?, ?> result =
          sourceClass.traverseProgramMethods(
              method -> {
                boolean foundInvokeSpecialToDefaultLibraryMethod =
                    method.registerCodeReferencesWithResult(
                        new InvokeSpecialToDefaultLibraryMethodUseRegistry(appView, method));
                return TraversalContinuation.breakIf(foundInvokeSpecialToDefaultLibraryMethod);
              });
      if (result.shouldBreak()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if {@param sourceClass} is a merge candidate. Note that the result of the checks
   * in this method may change in response to class merges. Therefore, this method should always be
   * called before merging {@param sourceClass} into {@param targetClass}.
   */
  boolean isStillMergeCandidate(DexProgramClass sourceClass, DexProgramClass targetClass) {
    assert !verticallyMergedClassesBuilder.isMergeTarget(sourceClass);
    // For interface types, this is more complicated, see:
    // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-5.html#jvms-5.5
    // We basically can't move the clinit, since it is not called when implementing classes have
    // their clinit called - except when the interface has a default method.
    if ((sourceClass.hasClassInitializer() && targetClass.hasClassInitializer())
        || targetClass.classInitializationMayHaveSideEffects(
            appView, type -> type.isIdenticalTo(sourceClass.getType()))
        || (sourceClass.isInterface()
            && sourceClass.classInitializationMayHaveSideEffects(appView))) {
      return false;
    }
    boolean sourceCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(sourceClass)
            || sourceClass.hasStaticSynchronizedMethods();
    boolean targetCanBeSynchronizedOn =
        appView.appInfo().isLockCandidate(targetClass)
            || targetClass.hasStaticSynchronizedMethods();
    if (sourceCanBeSynchronizedOn && targetCanBeSynchronizedOn) {
      return false;
    }
    if (targetClass.hasEnclosingMethodAttribute() || !targetClass.getInnerClasses().isEmpty()) {
      return false;
    }
    if (methodResolutionMayChange(sourceClass, targetClass)) {
      return false;
    }
    // Field resolution first considers the direct interfaces of [targetClass] before it proceeds
    // to the super class.
    if (fieldResolutionMayChange(sourceClass, targetClass)) {
      return false;
    }
    // Only merge if api reference level of source class is equal to target class. The check is
    // somewhat expensive.
    if (appView.options().apiModelingOptions().isApiCallerIdentificationEnabled()) {
      AndroidApiLevelCompute apiLevelCompute = appView.apiLevelCompute();
      ComputedApiLevel sourceApiLevel =
          getApiReferenceLevelForMerging(apiLevelCompute, sourceClass);
      ComputedApiLevel targetApiLevel =
          getApiReferenceLevelForMerging(apiLevelCompute, targetClass);
      if (!sourceApiLevel.equals(targetApiLevel)) {
        return false;
      }
    }
    return true;
  }

  private boolean disallowInlining(ProgramMethod method, DexProgramClass context) {
    if (!appView.options().inlinerOptions().enableInlining) {
      return true;
    }
    Code code = method.getDefinition().getCode();
    if (code.isCfCode()) {
      CfCode cfCode = code.asCfCode();
      ConstraintWithTarget constraint =
          cfCode.computeInliningConstraint(appView, appView.graphLens(), method);
      if (constraint.isNever()) {
        return true;
      }
      // Constructors can have references beyond the root main dex classes. This can increase the
      // size of the main dex dependent classes and we should bail out.
      if (mainDexInfo.disallowInliningIntoContext(appView, context, method)) {
        return true;
      }
      return false;
    }
    if (code.isDefaultInstanceInitializerCode()) {
      return false;
    }
    return true;
  }

  private boolean fieldResolutionMayChange(DexClass source, DexClass target) {
    if (source.getType().isIdenticalTo(target.getSuperType())) {
      // If there is a "iget Target.f" or "iput Target.f" instruction in target, and the class
      // Target implements an interface that declares a static final field f, this should yield an
      // IncompatibleClassChangeError.
      // TODO(christofferqa): In the following we only check if a static field from an interface
      //  shadows an instance field from [source]. We could actually check if there is an iget/iput
      //  instruction whose resolution would be affected by the merge. The situation where a static
      //  field shadows an instance field is probably not widespread in practice, though.
      FieldSignatureEquivalence equivalence = FieldSignatureEquivalence.get();
      Set<Wrapper<DexField>> staticFieldsInInterfacesOfTarget = new HashSet<>();
      for (DexType interfaceType : target.getInterfaces()) {
        DexClass clazz = appView.definitionFor(interfaceType);
        for (DexEncodedField staticField : clazz.staticFields()) {
          staticFieldsInInterfacesOfTarget.add(equivalence.wrap(staticField.getReference()));
        }
      }
      for (DexEncodedField instanceField : source.instanceFields()) {
        if (staticFieldsInInterfacesOfTarget.contains(
            equivalence.wrap(instanceField.getReference()))) {
          // An instruction "iget Target.f" or "iput Target.f" that used to hit a static field in an
          // interface would now hit an instance field from [source], so that an IncompatibleClass-
          // ChangeError would no longer be thrown. Abort merge.
          return true;
        }
      }
    }
    return false;
  }

  private boolean mergeMayLeadToIllegalAccesses(DexProgramClass source, DexProgramClass target) {
    if (source.isSamePackage(target)) {
      // When merging two classes from the same package, we only need to make sure that [source]
      // does not get less visible, since that could make a valid access to [source] from another
      // package illegal after [source] has been merged into [target].
      assert source.getAccessFlags().isPackagePrivateOrPublic();
      assert target.getAccessFlags().isPackagePrivateOrPublic();
      // TODO(b/287891322): Allow merging if `source` is only accessed from inside its own package.
      return source.getAccessFlags().isPublic() && target.getAccessFlags().isPackagePrivate();
    }

    // Check that all accesses to [source] and its members from inside the current package of
    // [source] will continue to work. This is guaranteed if [target] is public and all members of
    // [source] are either private or public.
    //
    // (Deliberately not checking all accesses to [source] since that would be expensive.)
    if (!target.isPublic()) {
      return true;
    }
    for (DexType sourceInterface : source.getInterfaces()) {
      DexClass sourceInterfaceClass = appView.definitionFor(sourceInterface);
      if (sourceInterfaceClass != null && !sourceInterfaceClass.isPublic()) {
        return true;
      }
    }
    for (DexEncodedField field : source.fields()) {
      if (!(field.isPublic() || field.isPrivate())) {
        return true;
      }
    }
    for (DexEncodedMethod method : source.methods()) {
      if (!(method.isPublic() || method.isPrivate())) {
        return true;
      }
      // Check if the target is overriding and narrowing the access.
      if (method.isPublic()) {
        DexEncodedMethod targetOverride = target.lookupVirtualMethod(method.getReference());
        if (targetOverride != null && !targetOverride.isPublic()) {
          return true;
        }
      }
    }
    // Check that all accesses from [source] to classes or members from the current package of
    // [source] will continue to work. This is guaranteed if the methods of [source] do not access
    // any private or protected classes or members from the current package of [source].
    TraversalContinuation<?, ?> result =
        source.traverseProgramMethods(
            method -> {
              boolean foundIllegalAccess =
                  method.registerCodeReferencesWithResult(
                      new IllegalAccessDetector(appView, method));
              if (foundIllegalAccess) {
                return TraversalContinuation.doBreak();
              }
              return TraversalContinuation.doContinue();
            });
    return result.shouldBreak();
  }

  private boolean methodResolutionMayChange(DexProgramClass source, DexProgramClass target) {
    for (DexEncodedMethod virtualSourceMethod : source.virtualMethods()) {
      DexEncodedMethod directTargetMethod =
          target.lookupDirectMethod(virtualSourceMethod.getReference());
      if (directTargetMethod != null) {
        // A private method shadows a virtual method. This situation is rare, since it is not
        // allowed by javac. Therefore, we just give up in this case. (In principle, it would be
        // possible to rename the private method in the subclass, and then move the virtual method
        // to the subclass without changing its name.)
        return true;
      }
    }

    // When merging an interface into a class, all instructions on the form "invoke-interface
    // [source].m" are changed into "invoke-virtual [target].m". We need to abort the merge if this
    // transformation could hide IncompatibleClassChangeErrors.
    if (source.isInterface() && !target.isInterface()) {
      List<DexEncodedMethod> defaultMethods = new ArrayList<>();
      for (DexEncodedMethod virtualMethod : source.virtualMethods()) {
        if (!virtualMethod.accessFlags.isAbstract()) {
          defaultMethods.add(virtualMethod);
        }
      }

      // For each of the default methods, the subclass [target] could inherit another default method
      // with the same signature from another interface (i.e., there is a conflict). In such cases,
      // instructions on the form "invoke-interface [source].foo()" will fail with an Incompatible-
      // ClassChangeError.
      //
      // Example:
      //   interface I1 { default void m() {} }
      //   interface I2 { default void m() {} }
      //   class C implements I1, I2 {
      //     ... invoke-interface I1.m ... <- IncompatibleClassChangeError
      //   }
      for (DexEncodedMethod method : defaultMethods) {
        // Conservatively find all possible targets for this method.
        LookupResultSuccess lookupResult =
            appView
                .appInfo()
                .resolveMethodOnInterfaceLegacy(method.getHolderType(), method.getReference())
                .lookupVirtualDispatchTargets(target, appView)
                .asLookupResultSuccess();
        assert lookupResult != null;
        if (lookupResult == null) {
          return true;
        }
        if (lookupResult.contains(method)) {
          Box<Boolean> found = new Box<>(false);
          lookupResult.forEach(
              interfaceTarget -> {
                if (ObjectUtils.identical(interfaceTarget.getDefinition(), method)) {
                  return;
                }
                DexClass enclosingClass = interfaceTarget.getHolder();
                if (enclosingClass != null && enclosingClass.isInterface()) {
                  // Found a default method that is different from the one in [source], aborting.
                  found.set(true);
                }
              },
              lambdaTarget -> {
                // The merger should already have excluded lambda implemented interfaces.
                assert false;
              });
          if (found.get()) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
