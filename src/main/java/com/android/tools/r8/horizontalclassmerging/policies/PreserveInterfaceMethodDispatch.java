// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static com.android.tools.r8.utils.TraversalContinuation.doContinue;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodResolution;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.DexClassAndMethodSet;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * When a default interface method overrides another default interface method, then horizontal class
 * merging can change resolution of default interface methods, in a way that leads to default
 * interface method invokes to target the wrong method at runtime.
 *
 * <p>The following is a simple example from B420228751Test. If the classes A and B are merged, we
 * effectively change A to also implement J. As a result, calls to a.m() will start dispatching to
 * J.m() instead of I.m().
 *
 * <pre>
 *   interface I { default void m() {} }
 *   interface J extends I { @Override default void m() {} }
 *   class A implements I {}
 *   class B implements J {}
 * </pre>
 *
 * <p>This policy addresses this issue by (1) computing the "default methods of interest", i.e., a
 * set of default interface method signatures that may change resolution after class merging, and
 * then (2) searches for a subclass of the merge group (including the merge group itself) where
 * resolution of one of the default methods of interest changes. If such a subclass is found,
 * merging is prohibited.
 */
public class PreserveInterfaceMethodDispatch extends MultiClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ImmediateProgramSubtypingInfo subtypingInfo;

  public PreserveInterfaceMethodDispatch(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo subtypingInfo) {
    this.appView = appView;
    this.subtypingInfo = subtypingInfo;
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  @Override
  public Collection<HorizontalMergeGroup> apply(HorizontalMergeGroup group) {
    if (group.isInterfaceGroup()) {
      return ImmutableList.of(group);
    }
    List<HorizontalMergeGroup> newGroups = new LinkedList<>();
    for (DexProgramClass clazz : group) {
      HorizontalMergeGroup classGroup = null;
      for (HorizontalMergeGroup newGroup : newGroups) {
        if (canAddClassToGroup(clazz, newGroup)) {
          classGroup = newGroup;
          break;
        }
      }
      if (classGroup == null) {
        classGroup = new HorizontalMergeGroup();
        newGroups.add(classGroup);
      }
      classGroup.add(clazz);
    }
    return removeTrivialGroups(newGroups);
  }

  private boolean canAddClassToGroup(DexProgramClass clazz, HorizontalMergeGroup group) {
    Set<DexType> groupInterfaces = Sets.newIdentityHashSet();
    Map<DexType, DexMethodSignatureSet> interfaceToDefaultInterfaceMethodsOfInterest =
        getDefaultInterfaceMethodsOfInterest(clazz, group, groupInterfaces);
    if (interfaceToDefaultInterfaceMethodsOfInterest.isEmpty()) {
      return true;
    }

    // Traverse the subclasses of the class.
    Collection<DexProgramClass> classGroup = List.of(clazz);
    return canAddInterfacesFromTo(classGroup, group, interfaceToDefaultInterfaceMethodsOfInterest)
        && canAddInterfacesFromTo(group, classGroup, interfaceToDefaultInterfaceMethodsOfInterest);
  }

  private boolean canAddInterfacesFromTo(
      Collection<DexProgramClass> fromGroup,
      Collection<DexProgramClass> toGroup,
      Map<DexType, DexMethodSignatureSet> interfaceToDefaultInterfaceMethodsOfInterest) {
    // Compute the set of interfaces in the target group.
    Set<DexType> fromGroupInterfaces =
        Sets.newLinkedHashSet(IterableUtils.flatMap(fromGroup, DexClass::getInterfaces));
    Set<DexType> toGroupInterfaces =
        Sets.newLinkedHashSet(IterableUtils.flatMap(toGroup, DexClass::getInterfaces));

    // Compute the set of default methods that is effectively added from the source group to the
    // target group.
    DexMethodSignatureSet defaultInterfaceMethodsOfInterest = DexMethodSignatureSet.create();
    interfaceToDefaultInterfaceMethodsOfInterest.forEach(
        (interfaceOfInterest, defaultInterfaceMethodsOfInterestFromInterface) -> {
          // The current `interfaceOfInterest` is in the diff of the interfaces of the source and
          // target group. If the target group does not implement this interface, then it is added
          // from the source group.
          if (!toGroupInterfaces.contains(interfaceOfInterest)) {
            assert fromGroup.stream()
                .anyMatch(fromClass -> fromClass.getInterfaces().contains(interfaceOfInterest));
            defaultInterfaceMethodsOfInterest.addAll(
                defaultInterfaceMethodsOfInterestFromInterface);
          }
        });

    // If there are no default interface methods of interest, then merging this way is safe.
    if (defaultInterfaceMethodsOfInterest.isEmpty()) {
      return true;
    }

    LinkedHashSet<DexType> newToGroupInterfaces = Sets.newLinkedHashSet(toGroupInterfaces);
    newToGroupInterfaces.addAll(fromGroupInterfaces);
    DexTypeList newToGroupInterfacesList = DexTypeList.create(newToGroupInterfaces);

    // Run a worklist algorithm to search for a subclass of the target group (including the classes
    // in the target group) where adding the interfaces from the source group would change method
    // resolution.
    Map<DexProgramClass, DexMethodSignatureSet> rootDefaultInterfaceMethodsOfInterest =
        MapUtils.transform(
            toGroup,
            IdentityHashMap::new,
            Function.identity(),
            ignoreArgument(() -> defaultInterfaceMethodsOfInterest));

    Set<DexProgramClass> roots = SetUtils.newIdentityHashSet(toGroup);
    WorkList<DexProgramClass> worklist = WorkList.newIdentityWorkList(toGroup);
    TraversalContinuation<?, ?> traversalContinuation =
        worklist.run(
            currentClass -> {
              DexMethodSignatureSet currentDefaultInterfaceMethodsOfInterest =
                  rootDefaultInterfaceMethodsOfInterest.remove(currentClass);
              assert currentDefaultInterfaceMethodsOfInterest != null;
              assert !currentDefaultInterfaceMethodsOfInterest.isEmpty();

              // Check if this class declares any of the default interface methods of interest.
              DexMethodSignatureSet newlyDeclaredClassMethods = null;
              for (DexEncodedMethod method :
                  currentClass.virtualMethods(DexEncodedMethod::isNonPrivateVirtualMethod)) {
                if (currentDefaultInterfaceMethodsOfInterest.contains(method)) {
                  if (newlyDeclaredClassMethods == null) {
                    newlyDeclaredClassMethods = DexMethodSignatureSet.create();
                  }
                  newlyDeclaredClassMethods.add(method);
                }
              }

              // Filter the default interface methods of interest for the remainder of the downwards
              // hierarchy traversal.
              DexMethodSignatureSet prunedDefaultInterfaceMethodsOfInterest;
              if (newlyDeclaredClassMethods != null) {
                prunedDefaultInterfaceMethodsOfInterest =
                    DexMethodSignatureSet.create(currentDefaultInterfaceMethodsOfInterest);
                prunedDefaultInterfaceMethodsOfInterest.removeAll(newlyDeclaredClassMethods);
              } else {
                prunedDefaultInterfaceMethodsOfInterest = currentDefaultInterfaceMethodsOfInterest;
              }

              // If there are no more default interface methods of interest, we do not need to
              // consider the remaining subtree.
              if (prunedDefaultInterfaceMethodsOfInterest.isEmpty()) {
                return TraversalContinuation.doContinue();
              }

              // For the remaining default interface methods of interest, analyze if resolution
              // changes at the current class when we add the implemented interface from the source
              // group to the target group.
              //
              // This can be skipped if the current class is not one of the roots and does not
              // implement any interfaces. (If a root does not itself implement any interfaces, a
              // superclass of the root could implement interfaces.)
              if (roots.contains(currentClass) || !currentClass.getInterfaces().isEmpty()) {
                for (DexMethodSignature signature : prunedDefaultInterfaceMethodsOfInterest) {
                  // First check that resolution succeeds prior to merging. It is not uncommon that
                  // resolution fails with a NoSuchMethodError, since the default interface methods
                  // added to this class as a result of a class merge operation does not need to be
                  // defined on this class prior to the merge. In this case we explicitly want to
                  // allow merging.
                  MethodResolutionResult resolutionResult =
                      appView.appInfo().resolveMethodOnClass(currentClass, signature);
                  if (!resolutionResult.isSingleResolution()) {
                    continue;
                  }

                  // We expect to resolve to an interface method, since we prune out the methods
                  // that are declared in the super class chain.
                  assert resolutionResult.getResolvedHolder().isInterface();

                  // Resolve the method while simulating that the target group implements the
                  // interfaces from the source group.
                  MethodResolutionResult newResolutionResult =
                      new MethodResolutionWithExtraInterfaces(
                              appView, toGroup, newToGroupInterfacesList)
                          .resolveMethodOnClass(
                              currentClass, signature.getProto(), signature.getName());

                  // If we resolved to a different method then disallow merging.
                  if (newResolutionResult.isSingleResolution()
                      && resolutionResult.getResolvedMethod()
                          != newResolutionResult.getResolvedMethod()) {
                    return TraversalContinuation.doBreak();
                  }
                }
              }

              // Enqueue subclasses.
              for (DexProgramClass subclass : subtypingInfo.getSubclasses(currentClass)) {
                rootDefaultInterfaceMethodsOfInterest.put(
                    subclass, prunedDefaultInterfaceMethodsOfInterest);
                worklist.addIfNotSeen(subclass);
              }

              return TraversalContinuation.doContinue();
            });

    // OK to merge if we did not abort.
    return traversalContinuation.shouldContinue();
  }

  // Linear bottom up traversal over the interface class hierarchy to find the set of method
  // signatures that appear as default interface methods on >= 2 interfaces.
  private Map<DexType, DexMethodSignatureSet> getDefaultInterfaceMethodsOfInterest(
      DexProgramClass clazz, HorizontalMergeGroup group, Set<DexType> groupInterfaces) {
    Set<DexType> interfacesOfInterest = Sets.newIdentityHashSet();

    // Iterate the interfaces of the group.
    for (DexProgramClass groupMember : group) {
      for (DexType implementedInterface : groupMember.getInterfaces()) {
        if (groupInterfaces.add(implementedInterface)
            && !clazz.getInterfaces().contains(implementedInterface)) {
          // By merging the candidate class and the group we would effectively add this interface to
          // the candidate class. Note that we require merge candidates to have the same superclass,
          // thus we should not need to consider the interfaces implemented by the superclasses of
          // the candidate class.
          interfacesOfInterest.add(implementedInterface);
        }
      }
    }

    // Iterate interfaces of the candidate class.
    for (DexType implementedInterface : clazz.getInterfaces()) {
      if (!groupInterfaces.contains(implementedInterface)) {
        interfacesOfInterest.add(implementedInterface);
      }
    }

    // If there are no interfaces of interest then merging is safe.
    if (interfacesOfInterest.isEmpty()) {
      return Collections.emptyMap();
    }

    // Filter out all methods that are declared in a super class of the candidate class.
    assert clazz.getSuperType().isIdenticalTo(group.getSuperType());
    DexMethodSignatureSet knownClassMethods = DexMethodSignatureSet.create();
    appView
        .appInfo()
        .traverseSuperClasses(
            clazz,
            (currentType, currentClass, immediateSubclass) -> {
              if (currentClass != null) {
                currentClass.forEachClassMethodMatching(
                    DexEncodedMethod::isNonPrivateVirtualMethod, knownClassMethods::add);
              }
              return doContinue();
            });

    Map<DexType, DexMethodSignatureSet> interfaceToDefaultInterfaceMethodsOfInterest =
        new IdentityHashMap<>();
    for (DexType implementedInterface : interfacesOfInterest) {
      interfaceToDefaultInterfaceMethodsOfInterest.put(
          implementedInterface,
          getDefaultInterfaceMethodsOfInterest(implementedInterface, knownClassMethods));
    }

    return interfaceToDefaultInterfaceMethodsOfInterest;
  }

  private DexMethodSignatureSet getDefaultInterfaceMethodsOfInterest(
      DexType implementedInterface, DexMethodSignatureSet knownClassMethods) {
    DexMethodSignatureMap<DexClassAndMethodSet> defaultInterfaceMethodsOfInterest =
        DexMethodSignatureMap.create();
    WorkList<DexType> worklist = WorkList.newIdentityWorkList(implementedInterface);
    worklist.process(
        currentInterfaceType -> {
          DexClass currentInterface = appView.definitionFor(currentInterfaceType);
          if (currentInterface == null) {
            return;
          }
          currentInterface.forEachClassMethodMatching(
              method -> method.isDefaultMethod() && !knownClassMethods.contains(method),
              method ->
                  defaultInterfaceMethodsOfInterest
                      .computeIfAbsent(method, ignoreKey(DexClassAndMethodSet::create))
                      .add(method));
          worklist.addIfNotSeen(currentInterface.getInterfaces());
        });
    defaultInterfaceMethodsOfInterest.removeIf((signature, methods) -> methods.size() == 1);
    return defaultInterfaceMethodsOfInterest.keySignatureSet();
  }

  @Override
  public boolean shouldSkipPolicy() {
    return !appView.options().canUseDefaultAndStaticInterfaceMethods();
  }

  @Override
  public String getName() {
    return "PreserveInterfaceMethodDispatch";
  }

  // A custom resolution implementation that supports simulating that a given class implements a
  // given list of interfaces.
  private static class MethodResolutionWithExtraInterfaces extends MethodResolution {

    private final Collection<DexProgramClass> toGroup;
    private final DexTypeList toGroupInterfaces;

    private MethodResolutionWithExtraInterfaces(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        Collection<DexProgramClass> toGroup,
        DexTypeList toGroupInterfaces) {
      super(
          appView.appInfo()::contextIndependentDefinitionForWithResolutionResult,
          appView.dexItemFactory());
      this.toGroup = toGroup;
      this.toGroupInterfaces = toGroupInterfaces;
    }

    @Override
    protected DexTypeList getInterfaces(DexClass clazz) {
      return clazz.isProgramClass() && toGroup.contains(clazz.asProgramClass())
          ? toGroupInterfaces
          : clazz.getInterfaces();
    }
  }
}
