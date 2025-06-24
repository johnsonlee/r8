// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.features.ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap;
import static com.android.tools.r8.utils.BiPredicateUtils.alwaysFalse;
import static com.android.tools.r8.utils.TraversalContinuation.breakIf;
import static com.android.tools.r8.utils.TraversalContinuation.doBreak;
import static com.android.tools.r8.utils.TraversalContinuation.doContinue;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection.Builder;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.shaking.MissingClasses;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.TriFunction;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;
import java.util.function.Function;

/* Specific subclass of AppInfo designed to support desugaring in D8. Desugaring requires a
 * minimal amount of knowledge in the overall program, provided through classpath. Basic
 * features are present, such as static and super look-ups, or isSubtype.
 */
public class AppInfoWithClassHierarchy extends AppInfo {

  private static final CreateDesugaringViewOnAppInfo WITNESS = new CreateDesugaringViewOnAppInfo();

  static class CreateDesugaringViewOnAppInfo {
    private CreateDesugaringViewOnAppInfo() {}
  }

  public static AppInfoWithClassHierarchy createInitialAppInfoWithClassHierarchy(
      DexApplication application,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      MainDexInfo mainDexInfo,
      GlobalSyntheticsStrategy globalSyntheticsStrategy) {
    return new AppInfoWithClassHierarchy(
        SyntheticItems.createInitialSyntheticItems(application, globalSyntheticsStrategy),
        classToFeatureSplitMap,
        mainDexInfo,
        MissingClasses.empty());
  }

  /** Set of types that are mentioned in the program, but for which no definition exists. */
  // TODO(b/175659048): Consider hoisting to AppInfo to allow using MissingClasses in D8 desugar.
  private MissingClasses missingClasses;

  // For AppInfoWithLiveness subclass.
  protected AppInfoWithClassHierarchy(
      CommittedItems committedItems,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      MainDexInfo mainDexInfo,
      MissingClasses missingClasses) {
    super(classToFeatureSplitMap, committedItems, mainDexInfo);
    this.missingClasses = missingClasses;
  }

  // For desugaring.
  private AppInfoWithClassHierarchy(CreateDesugaringViewOnAppInfo witness, AppInfo appInfo) {
    super(witness, appInfo, createEmptyClassToFeatureSplitMap());
    // TODO(b/175659048): Migrate the reporting of missing classes in D8 desugar to MissingClasses,
    //  and use the missing classes from AppInfo instead of MissingClasses.empty().
    this.missingClasses = MissingClasses.empty();
  }

  public static AppInfoWithClassHierarchy createForDesugaring(AppInfo appInfo) {
    assert !appInfo.hasClassHierarchy();
    return new AppInfoWithClassHierarchy(WITNESS, appInfo);
  }

  @Override
  protected AppInfoWithClassHierarchy rebuild(DexApplication application) {
    return rebuildWithCommittedItems(getSyntheticItems().commit(application));
  }

  @Override
  public AppInfoWithClassHierarchy rebuildWithCommittedItems(CommittedItems commit) {
    return new AppInfoWithClassHierarchy(
        commit, getClassToFeatureSplitMap(), getMainDexInfo(), getMissingClasses());
  }

  public void notifyMinifierFinished() {
    // Intentionally empty.
  }

  public AppInfoWithClassHierarchy rebuildWithClassHierarchy(
      Function<DexApplication, DexApplication> fn) {
    assert checkIfObsolete();
    return new AppInfoWithClassHierarchy(
        getSyntheticItems().commit(fn.apply(app())),
        getClassToFeatureSplitMap(),
        getMainDexInfo(),
        getMissingClasses());
  }

  @Override
  public AppInfoWithClassHierarchy rebuildWithMainDexInfo(MainDexInfo mainDexInfo) {
    assert getClass() == AppInfoWithClassHierarchy.class;
    assert checkIfObsolete();
    return new AppInfoWithClassHierarchy(
        getSyntheticItems().commit(app()),
        getClassToFeatureSplitMap(),
        mainDexInfo,
        getMissingClasses());
  }

  @Override
  public AppInfoWithClassHierarchy prunedCopyFrom(
      PrunedItems prunedItems, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    assert getClass() == AppInfoWithClassHierarchy.class;
    assert checkIfObsolete();
    assert prunedItems.getPrunedApp() == app();
    if (prunedItems.isEmpty()) {
      return this;
    }
    timing.begin("Pruning AppInfoWithClassHierarchy");
    AppInfoWithClassHierarchy result =
        new AppInfoWithClassHierarchy(
            getSyntheticItems().commitPrunedItems(prunedItems),
            getClassToFeatureSplitMap().withoutPrunedItems(prunedItems),
            getMainDexInfo().withoutPrunedItems(prunedItems),
            getMissingClasses());
    timing.end();
    return result;
  }

  public MissingClasses getMissingClasses() {
    return missingClasses;
  }

  public void setMissingClasses(MissingClasses missingClasses) {
    this.missingClasses = missingClasses;
  }

  @Override
  public boolean hasClassHierarchy() {
    assert checkIfObsolete();
    return true;
  }

  @Override
  public AppInfoWithClassHierarchy withClassHierarchy() {
    assert checkIfObsolete();
    return this;
  }

  public boolean hasNonTrivialFinalizeMethod(DexClass clazz) {
    if (clazz.isInterface()) {
      return false;
    }
    DexClassAndMethod resolvedMethod =
        resolveMethodOnClass(clazz, dexItemFactory().objectMembers.finalize).getResolutionPair();
    return resolvedMethod != null
        && resolvedMethod.getHolderType().isNotIdenticalTo(dexItemFactory().enumType)
        && resolvedMethod.getHolderType().isNotIdenticalTo(dexItemFactory().objectType);
  }

  /** Primitive traversal over all (non-interface) superclasses of a given type. */
  public <B> TraversalContinuation<B, ?> traverseSuperClasses(
      DexClass clazz, TriFunction<DexType, DexClass, DexClass, TraversalContinuation<B, ?>> fn) {
    DexClass currentClass = clazz;
    while (currentClass != null && currentClass.getSuperType() != null) {
      DexClass superclass = definitionFor(currentClass.getSuperType());
      TraversalContinuation<B, ?> stepResult =
          fn.apply(currentClass.getSuperType(), superclass, currentClass);
      if (stepResult.shouldBreak()) {
        return stepResult;
      }
      currentClass = superclass;
    }
    return doContinue();
  }

  public interface TraverseSuperTypesCallback<TB, TC> {

    TraversalContinuation<TB, TC> apply(
        DexType supertype, DexClass superclassOrNull, DexClass context, boolean isInterface);
  }

  public <B> TraversalContinuation<B, ?> traverseSuperInterfaces(
      DexClass clazz, TraverseSuperTypesCallback<B, ?> fn) {
    return traverseSuperInterfaces(clazz, fn, alwaysFalse());
  }

  /**
   * Primitive traversal over all superinterfaces of a given type.
   *
   * <p>No order is guaranteed for the traversal, but a given type will be visited at most once. The
   * given type is *not* visited. The function indicates if traversal should continue or break. The
   * result of the traversal is BREAK iff the function returned BREAK.
   *
   * @param stoppingCriterion if this predicate returns true for a given class, the traversal will
   *     not be applied to the class or any of its supertypes. All supertypes of the given class
   *     that are lower in the hierarchy than classes matched by the stopping criterion will still
   *     be visited.
   */
  public <B> TraversalContinuation<B, ?> traverseSuperInterfaces(
      DexClass clazz,
      TraverseSuperTypesCallback<B, ?> fn,
      BiPredicate<DexType, DexClass> stoppingCriterion) {
    return internalTraverseSuperTypes(clazz, fn, stoppingCriterion, true);
  }

  public <B> TraversalContinuation<B, ?> traverseSuperTypes(
      DexClass clazz, TraverseSuperTypesCallback<B, ?> fn) {
    return traverseSuperTypes(clazz, fn, alwaysFalse());
  }

  /**
   * Primitive traversal over all supertypes (including interfaces) of a given type.
   *
   * <p>No order is guaranteed for the traversal, but a given type will be visited at most once. The
   * given type is *not* visited. The function indicates if traversal should continue or break. The
   * result of the traversal is BREAK iff the function returned BREAK.
   *
   * @param stoppingCriterion if this predicate returns true for a given class, the traversal will
   *     not be applied to the class or any of its supertypes. All supertypes of the given class
   *     that are lower in the hierarchy than classes matched by the stopping criterion will still
   *     be visited.
   */
  public <B> TraversalContinuation<B, ?> traverseSuperTypes(
      DexClass clazz,
      TraverseSuperTypesCallback<B, ?> fn,
      BiPredicate<DexType, DexClass> stoppingCriterion) {
    return internalTraverseSuperTypes(clazz, fn, stoppingCriterion, false);
  }

  public <B> TraversalContinuation<B, ?> internalTraverseSuperTypes(
      DexClass clazz,
      TraverseSuperTypesCallback<B, ?> fn,
      BiPredicate<DexType, DexClass> stoppingCriterion,
      boolean interfacesOnly) {
    // We do an initial zero-allocation pass over the class super chain as it does not require a
    // worklist/seen-set. Only if the traversal is not aborted and there actually are interfaces,
    // do we continue traversal over the interface types. This is assuming that the second pass
    // over the super chain is less expensive than the eager allocation of the worklist.
    DexType stoppingCriterionClassResult = null;
    if (!interfacesOnly) {
      boolean seenInterfaces = false;
      DexClass currentClass = clazz;
      while (currentClass != null) {
        if (!currentClass.getInterfaces().isEmpty()) {
          seenInterfaces = true;
        }
        if (!currentClass.hasSuperType()) {
          break;
        }
        DexType supertype = currentClass.getSuperType();
        DexClass superclass = definitionFor(supertype);
        // If the stopping criterion matches the current superclass, then stop the upwards traversal
        // at this point. Note that at this point we have already forwarded the subclasses to the
        // consumer, which is WAI.
        if (stoppingCriterion.test(supertype, superclass)) {
          stoppingCriterionClassResult = supertype;
          break;
        }
        TraversalContinuation<B, ?> stepResult =
            fn.apply(supertype, superclass, currentClass, false);
        if (stepResult.shouldBreak()) {
          return stepResult;
        }
        currentClass = definitionFor(currentClass.getSuperType());
      }
      if (!seenInterfaces) {
        return doContinue();
      }
    }
    // Interfaces exist (or the superclass traversal has been skipped). Create a worklist with a
    // seen set to ensure single visits.
    Set<DexType> seenInterfaces = Sets.newIdentityHashSet();
    Deque<DexClass> worklist = new ArrayDeque<>();
    // Populate the worklist with the direct interfaces of the super chain.
    {
      DexClass currentClass = clazz;
      while (currentClass != null) {
        TraversalContinuation<B, ?> traversalContinuation =
            internalTraverseInterfaces(
                currentClass, fn, stoppingCriterion, seenInterfaces, worklist);
        if (traversalContinuation.shouldBreak()) {
          return traversalContinuation;
        }
        if (!currentClass.hasSuperType()) {
          break;
        }
        DexType supertype = currentClass.getSuperType();
        if (interfacesOnly) {
          // The stoppingCriterionClassResult has not been set above.
          assert stoppingCriterionClassResult == null;
          DexClass superclass = definitionFor(supertype);
          if (stoppingCriterion.test(supertype, superclass)) {
            break;
          }
          currentClass = superclass;
        } else if (supertype.isIdenticalTo(stoppingCriterionClassResult)) {
          break;
        } else {
          currentClass = definitionFor(supertype);
        }
      }
    }
    // Iterate all interfaces.
    while (!worklist.isEmpty()) {
      DexClass currentInterface = worklist.removeFirst();
      TraversalContinuation<B, ?> traversalContinuation =
          internalTraverseInterfaces(
              currentInterface, fn, stoppingCriterion, seenInterfaces, worklist);
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    return doContinue();
  }

  private <B> TraversalContinuation<B, ?> internalTraverseInterfaces(
      DexClass context,
      TraverseSuperTypesCallback<B, ?> fn,
      BiPredicate<DexType, DexClass> stoppingCriterion,
      Set<DexType> seenInterfaces,
      Deque<DexClass> worklist) {
    assert !stoppingCriterion.test(context.getType(), context);
    for (DexType interfaceType : context.getInterfaces()) {
      if (seenInterfaces.add(interfaceType)) {
        DexClass interfaceClass = definitionFor(interfaceType);
        if (stoppingCriterion.test(interfaceType, interfaceClass)) {
          continue;
        }
        TraversalContinuation<B, ?> stepResult =
            fn.apply(interfaceType, interfaceClass, context, true);
        if (stepResult.shouldBreak()) {
          return stepResult;
        }
        if (interfaceClass != null) {
          worklist.addLast(interfaceClass);
        }
      }
    }
    return doContinue();
  }

  public boolean isSubtype(DexType subtype, DexType supertype) {
    return isSubtype(subtype, supertype, null, null, null);
  }

  public boolean isSubtype(DexType subtype, DexClass superclass) {
    return isSubtype(subtype, superclass.getType(), null, superclass, null);
  }

  public boolean isSubtype(
      DexType subtype, DexClass superclass, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    return isSubtype(subtype, superclass.getType(), null, superclass, immediateSubtypingInfo);
  }

  public boolean isSubtype(DexClass subclass, DexClass superclass) {
    return isSubtype(subclass.getType(), superclass.getType(), subclass, superclass);
  }

  private boolean isSubtype(
      DexType subtype, DexType supertype, DexClass optionalSubclass, DexClass optionalSuperclass) {
    return isSubtype(subtype, supertype, optionalSubclass, optionalSuperclass, null);
  }

  private boolean isSubtype(
      DexType subtype,
      DexType supertype,
      DexClass optionalSubclass,
      DexClass optionalSuperclass,
      ImmediateProgramSubtypingInfo optionalImmediateSubtypingInfo) {
    assert subtype.isClassType();
    assert supertype.isClassType();
    return subtype.isIdenticalTo(supertype)
        || isStrictSubtypeOf(
            subtype,
            supertype,
            optionalSubclass,
            optionalSuperclass,
            optionalImmediateSubtypingInfo);
  }

  public boolean isStrictSubtypeOf(DexType subtype, DexType supertype) {
    return isStrictSubtypeOf(subtype, supertype, null, null, null);
  }

  private boolean isStrictSubtypeOf(
      DexType subtype,
      DexType supertype,
      DexClass optionalSubclass,
      DexClass optionalSuperclass,
      ImmediateProgramSubtypingInfo optionalImmediateSubtypingInfo) {
    assert subtype.isClassType();
    assert supertype.isClassType();
    assert optionalSubclass == null || optionalSubclass.getType().isIdenticalTo(subtype);
    assert optionalSuperclass == null || optionalSuperclass.getType().isIdenticalTo(supertype);
    if (subtype.isIdenticalTo(supertype)) {
      return false;
    }
    // Treat object special: it is always the supertype even for broken hierarchies.
    DexType objectType = dexItemFactory().objectType;
    if (subtype.isIdenticalTo(objectType)) {
      return false;
    }
    if (supertype.isIdenticalTo(objectType)) {
      return true;
    }
    // No need to traverse the ancestors of `subtype` if the superclass is (effectively) final.
    if (optionalSuperclass == null) {
      optionalSuperclass = definitionFor(supertype);
      if (optionalSuperclass == null) {
        return false;
      }
    }
    DexClass superclass = optionalSuperclass;
    if (superclass.isFinal()) {
      return false;
    }
    if (optionalImmediateSubtypingInfo != null && superclass.isProgramClass()) {
      DexProgramClass programSuperclass = superclass.asProgramClass();
      if (optionalImmediateSubtypingInfo.getSubclasses(programSuperclass).isEmpty()) {
        return false;
      }
    }
    // Fast-past interface less-than non-interface checks.
    if (optionalSubclass == null) {
      optionalSubclass = definitionFor(subtype);
      if (optionalSubclass == null) {
        return false;
      }
    }
    DexClass subclass = optionalSubclass;
    if (subclass.isInterface() && !superclass.isInterface()) {
      assert !superclass.getType().isIdenticalTo(objectType);
      return false;
    }
    // Fast-path library less-than program checks.
    boolean isLibraryAboveProgram =
        options().isFullMode()
            && !options().getTestingOptions().allowLibraryExtendsProgramInFullMode;
    if (isLibraryAboveProgram && subclass.isLibraryClass() && superclass.isProgramClass()) {
      return false;
    }
    // Check all ancestors of the subclass.
    // TODO(b/123506120): Report missing types when the predicate is inconclusive.
    if (superclass.isInterface()) {
      // Check all super interfaces of the subclass.
      BiPredicate<DexType, DexClass> stoppingCriterion =
          isLibraryAboveProgram && superclass.isProgramClass()
              ? (supertypeOfSubclass, superclassOfSubclass) ->
                  superclassOfSubclass != null && superclassOfSubclass.isLibraryClass()
              : alwaysFalse();
      return traverseSuperInterfaces(
              subclass,
              (supertypeOfSubclass, superclassOfSubclass, context, isInterface) ->
                  breakIf(supertypeOfSubclass.isIdenticalTo(supertype)),
              stoppingCriterion)
          .shouldBreak();
    } else {
      // Check the superclass chain of the subclass.
      TraversalContinuation<Boolean, ?> result =
          traverseSuperClasses(
              subclass,
              (supertypeOfSubclass, superclassOfSubclass, context) -> {
                if (supertypeOfSubclass.isIdenticalTo(supertype)) {
                  return doBreak(true);
                }
                if (superclassOfSubclass == null) {
                  return doBreak(false);
                }
                if (isLibraryAboveProgram
                    && superclass.isProgramClass()
                    && superclassOfSubclass.isLibraryClass()) {
                  return doBreak(false);
                }
                return doContinue();
              });
      return result.isBreak() && result.asBreak().getValue();
    }
  }

  public boolean inSameHierarchy(DexType type, DexType other) {
    assert type.isClassType();
    assert other.isClassType();
    return isSubtype(type, other) || isSubtype(other, type);
  }

  public boolean inDifferentHierarchy(DexType type1, DexType type2) {
    return !inSameHierarchy(type1, type2);
  }

  public boolean isMissingOrHasMissingSuperType(DexType type) {
    DexClass clazz = definitionFor(type);
    return clazz == null || clazz.hasMissingSuperType(this);
  }

  /** Collect all interfaces that this type directly or indirectly implements. */
  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  public InterfaceCollection implementedInterfaces(DexType type) {
    assert type.isClassType();
    DexClass clazz = definitionFor(type);
    if (clazz == null) {
      return InterfaceCollection.empty();
    }

    // Fast path for a type below object with no interfaces.
    if (clazz.superType == dexItemFactory().objectType && clazz.interfaces.isEmpty()) {
      return clazz.isInterface()
          ? InterfaceCollection.singleton(type)
          : InterfaceCollection.empty();
    }

    // Slow path traverses the full super type hierarchy.
    Builder builder = InterfaceCollection.builder();
    if (clazz.isInterface()) {
      builder.addInterface(type, true);
    }
    // First find all interface leafs from the class super-type chain.
    Set<DexType> seenAndKnown = Sets.newIdentityHashSet();
    @SuppressWarnings("ReferenceEquality")
    Deque<Pair<DexClass, Boolean>> worklist = new ArrayDeque<>();
    {
      DexClass implementor = clazz;
      while (implementor != null) {
        for (DexType iface : implementor.interfaces) {
          if (seenAndKnown.contains(iface)) {
            continue;
          }
          boolean isKnown =
              InterfaceCollection.isKnownToImplement(iface, implementor.getType(), options());
          builder.addInterface(iface, isKnown);
          if (isKnown) {
            seenAndKnown.add(iface);
          }
          DexClass definition = definitionFor(iface);
          if (definition != null && !definition.interfaces.isEmpty()) {
            worklist.add(new Pair<>(definition, isKnown));
          }
        }
        if (implementor.superType == null
            || implementor.superType == options().dexItemFactory().objectType) {
          break;
        }
        implementor = definitionFor(implementor.superType);
      }
    }
    // Second complete the worklist of interfaces. All paths must be visited as an interface may
    // be unknown on one but not on another.
    while (!worklist.isEmpty()) {
      Pair<DexClass, Boolean> item = worklist.poll();
      DexClass implementor = item.getFirst();
      assert !implementor.interfaces.isEmpty();
      for (DexType itf : implementor.interfaces) {
        if (seenAndKnown.contains(itf)) {
          continue;
        }
        // A derived interface is known only if the full chain leading to it is known.
        boolean isKnown =
            item.getSecond()
                && InterfaceCollection.isKnownToImplement(itf, implementor.getType(), options());
        builder.addInterface(itf, isKnown);
        if (isKnown) {
          seenAndKnown.add(itf);
        }
        DexClass definition = definitionFor(itf);
        if (definition != null && !definition.interfaces.isEmpty()) {
          worklist.add(new Pair<>(definition, isKnown));
        }
      }
    }
    return builder.build();
  }

  public boolean isExternalizable(DexType type) {
    return isSubtype(type, dexItemFactory().externalizableType);
  }

  public boolean isSerializable(DexType type) {
    return isSubtype(type, dexItemFactory().serializableType);
  }

  public List<DexProgramClass> computeProgramClassRelationChain(
      DexProgramClass subClass, DexProgramClass superClass) {
    assert isSubtype(subClass.type, superClass.type);
    assert !subClass.isInterface();
    if (!superClass.isInterface()) {
      return computeChainInClassHierarchy(subClass, superClass.type);
    }
    // If the super type is an interface we first compute the program chain upwards, and in a
    // top-down order check if the interface is a super-type to the class. Computing it this way
    // guarantees to find the instantiated program-classes of the longest chain.
    List<DexProgramClass> relationChain =
        computeChainInClassHierarchy(subClass, dexItemFactory().objectType);
    WorkList<DexType> interfaceWorklist = WorkList.newIdentityWorkList();
    for (int i = relationChain.size() - 1; i >= 0; i--) {
      DexProgramClass clazz = relationChain.get(i);
      if (isInterfaceInSuperTypes(clazz, superClass.type, interfaceWorklist)) {
        return relationChain.subList(0, i + 1);
      }
    }
    return Collections.emptyList();
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isInterfaceInSuperTypes(
      DexProgramClass clazz, DexType ifaceToFind, WorkList<DexType> workList) {
    workList.addIfNotSeen(clazz.allImmediateSupertypes());
    while (workList.hasNext()) {
      DexType superType = workList.next();
      if (superType == ifaceToFind) {
        return true;
      }
      DexClass superClass = definitionFor(superType);
      if (superClass != null) {
        workList.addIfNotSeen(superClass.allImmediateSupertypes());
      }
    }
    return false;
  }

  @SuppressWarnings("ReferenceEquality")
  private List<DexProgramClass> computeChainInClassHierarchy(
      DexProgramClass subClass, DexType superType) {
    assert isSubtype(subClass.type, superType);
    assert !subClass.isInterface();
    assert superType == dexItemFactory().objectType
        || definitionFor(superType) == null
        || !definitionFor(superType).isInterface();
    List<DexProgramClass> relationChain = new ArrayList<>();
    DexClass current = subClass;
    while (current != null) {
      if (current.isProgramClass()) {
        relationChain.add(current.asProgramClass());
      }
      if (current.type == superType) {
        return relationChain;
      }
      current = definitionFor(current.superType);
    }
    return relationChain;
  }

  public boolean methodDefinedInInterfaces(DexEncodedMethod method, DexType implementingClass) {
    DexClass holder = definitionFor(implementingClass);
    if (holder == null) {
      return false;
    }
    for (DexType iface : holder.interfaces.values) {
      if (methodDefinedInInterface(method, iface)) {
        return true;
      }
    }
    return false;
  }

  public boolean methodDefinedInInterface(DexEncodedMethod method, DexType iface) {
    DexClass potentialHolder = definitionFor(iface);
    if (potentialHolder == null) {
      return false;
    }
    assert potentialHolder.isInterface();
    for (DexEncodedMethod virtualMethod : potentialHolder.virtualMethods()) {
      if (virtualMethod.getReference().match(method.getReference())
          && virtualMethod.isSameVisibility(method)) {
        return true;
      }
    }
    for (DexType parentInterface : potentialHolder.interfaces.values) {
      if (methodDefinedInInterface(method, parentInterface)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Helper method used for emulated interface resolution (not in JVM specifications). The result
   * may be abstract.
   */
  public DexClassAndMethod lookupMaximallySpecificMethod(DexClass clazz, DexMethod method) {
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .lookupMaximallySpecificTarget(clazz, method);
  }

  /**
   * Helper methods used for emulated interface resolution (not in JVM specifications). Answers the
   * abstract interface methods that the resolution could but does not necessarily resolve into.
   */
  public List<Entry<DexClass, DexEncodedMethod>> getAbstractInterfaceMethods(
      DexClass clazz, DexMethod method) {
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .getAbstractInterfaceMethods(clazz, method);
  }

  MethodResolutionResult resolveMaximallySpecificTarget(DexClass clazz, DexMethod method) {
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .resolveMaximallySpecificTarget(clazz, method);
  }

  MethodResolutionResult resolveMaximallySpecificTarget(LambdaDescriptor lambda, DexMethod method) {
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .resolveMaximallySpecificTarget(lambda, method);
  }

  /**
   * Lookup instance field starting in type and following the interface and super chain.
   *
   * <p>The result is the field that will be hit at runtime, if such field is known. A result of
   * null indicates that the field is either undefined or not an instance field.
   */
  public DexEncodedField lookupInstanceTargetOn(DexType type, DexField field) {
    assert checkIfObsolete();
    assert type.isClassType();
    DexEncodedField result = resolveFieldOn(type, field).getResolvedField();
    return result == null || result.accessFlags.isStatic() ? null : result;
  }

  public DexEncodedField lookupInstanceTarget(DexField field) {
    return lookupInstanceTargetOn(field.holder, field);
  }

  /**
   * Lookup static field starting in type and following the interface and super chain.
   *
   * <p>The result is the field that will be hit at runtime, if such field is known. A result of
   * null indicates that the field is either undefined or not a static field.
   */
  public DexClassAndField lookupStaticTargetOn(DexType type, DexField field) {
    assert checkIfObsolete();
    assert type.isClassType();
    DexClassAndField result = resolveFieldOn(type, field).getResolutionPair();
    return result == null || !result.getAccessFlags().isStatic() ? null : result;
  }

  public DexClassAndField lookupStaticTarget(DexField field) {
    return lookupStaticTargetOn(field.getHolderType(), field);
  }

  /**
   * Lookup static method following the super chain from the holder of {@code method}.
   *
   * <p>This method will resolve the method on the holder of {@code method} and only return a
   * non-null value if the result of resolution was a static, non-abstract method.
   *
   * @param method the method to lookup
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexClassAndMethod lookupStaticTarget(
      DexMethod method,
      DexProgramClass context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupStaticTarget(method, context, appView, appView.appInfo());
  }

  public DexClassAndMethod lookupStaticTarget(
      DexMethod method,
      DexProgramClass context,
      AppView<?> appView,
      AppInfoWithClassHierarchy appInfo) {
    assert checkIfObsolete();
    return unsafeResolveMethodDueToDexFormatLegacy(method)
        .lookupInvokeStaticTarget(context, appView, appInfo);
  }

  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexClassAndMethod lookupStaticTarget(
      DexMethod method,
      ProgramMethod context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupStaticTarget(method, context.getHolder(), appView);
  }

  public DexClassAndMethod lookupStaticTarget(
      DexMethod method,
      ProgramMethod context,
      AppView<?> appView,
      AppInfoWithClassHierarchy appInfo) {
    return lookupStaticTarget(method, context.getHolder(), appView, appInfo);
  }

  /**
   * Lookup super method following the super chain from the holder of {@code method}.
   *
   * <p>This method will resolve the method on the holder of {@code method} and only return a
   * non-null value if the result of resolution was an instance (i.e. non-static) method.
   *
   * @param method the method to lookup
   * @param context the class the invoke is contained in, i.e., the holder of the caller.
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexClassAndMethod lookupSuperTarget(
      DexMethod method,
      DexProgramClass context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupSuperTarget(method, context, appView, appView.appInfo());
  }

  public DexClassAndMethod lookupSuperTarget(
      DexMethod method,
      DexProgramClass context,
      AppView<?> appView,
      AppInfoWithClassHierarchy appInfo) {
    assert checkIfObsolete();
    return unsafeResolveMethodDueToDexFormatLegacy(method)
        .lookupInvokeSuperTarget(context, appView, appInfo);
  }

  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public final DexClassAndMethod lookupSuperTarget(
      DexMethod method,
      ProgramMethod context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupSuperTarget(method, context, appView, appView.appInfo());
  }

  public final DexClassAndMethod lookupSuperTarget(
      DexMethod method,
      ProgramMethod context,
      AppView<?> appView,
      AppInfoWithClassHierarchy appInfo) {
    return lookupSuperTarget(method, context.getHolder(), appView, appInfo);
  }

  /**
   * Lookup direct method following the super chain from the holder of {@code method}.
   *
   * <p>This method will lookup private and constructor methods.
   *
   * @param method the method to lookup
   * @return The actual target for {@code method} or {@code null} if none found.
   */
  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexClassAndMethod lookupDirectTarget(
      DexMethod method,
      DexProgramClass context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupDirectTarget(method, context, appView, appView.appInfo());
  }

  public DexClassAndMethod lookupDirectTarget(
      DexMethod method,
      DexProgramClass context,
      AppView<?> appView,
      AppInfoWithClassHierarchy appInfo) {
    assert checkIfObsolete();
    return unsafeResolveMethodDueToDexFormatLegacy(method)
        .lookupInvokeDirectTarget(context, appView, appInfo);
  }

  // TODO(b/155968472): This should take a parameter `boolean isInterface` and use resolveMethod().
  public DexClassAndMethod lookupDirectTarget(
      DexMethod method,
      ProgramMethod context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return lookupDirectTarget(method, context, appView, appView.appInfo());
  }

  public DexClassAndMethod lookupDirectTarget(
      DexMethod method,
      ProgramMethod context,
      AppView<?> appView,
      AppInfoWithClassHierarchy appInfo) {
    return lookupDirectTarget(method, context.getHolder(), appView, appInfo);
  }

  /**
   * This method will query the definition of the holder to decide on which resolution to use.
   *
   * <p>This is to overcome the shortcoming of the DEX file format that does not allow to encode the
   * kind of a method reference.
   */
  public MethodResolutionResult unsafeResolveMethodDueToDexFormatLegacy(DexMethod method) {
    assert checkIfObsolete();
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .unsafeResolveMethodDueToDexFormat(method);
  }

  public MethodResolutionResult resolveMethodLegacy(DexMethod invokedMethod, boolean isInterface) {
    assert checkIfObsolete();
    return resolveMethodOnLegacy(invokedMethod.getHolderType(), invokedMethod, isInterface);
  }

  public MethodResolutionResult resolveMethodOn(DexClass clazz, DexMethod method) {
    assert checkIfObsolete();
    return clazz.isInterface()
        ? resolveMethodOnInterface(clazz, method)
        : resolveMethodOnClass(clazz, method);
  }

  public MethodResolutionResult resolveMethodOnLegacy(DexClass clazz, DexMethod method) {
    assert checkIfObsolete();
    return clazz.isInterface()
        ? resolveMethodOnInterfaceLegacy(clazz, method)
        : resolveMethodOnClassLegacy(clazz, method);
  }

  public MethodResolutionResult resolveMethodOnLegacy(
      DexClass clazz, DexMethodSignature methodSignature) {
    assert checkIfObsolete();
    return clazz.isInterface()
        ? resolveMethodOnInterfaceLegacy(clazz, methodSignature)
        : resolveMethodOnClassLegacy(clazz, methodSignature);
  }

  public MethodResolutionResult resolveMethodOnLegacy(
      DexType holder, DexMethod method, boolean isInterface) {
    assert checkIfObsolete();
    return isInterface
        ? resolveMethodOnInterfaceLegacy(holder, method)
        : resolveMethodOnClassLegacy(holder, method);
  }

  public MethodResolutionResult resolveMethodOnClassHolderLegacy(DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnClassLegacy(method.getHolderType(), method);
  }

  public MethodResolutionResult resolveMethodOnClassLegacy(DexType holder, DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnClassLegacy(holder, method.getProto(), method.getName());
  }

  public MethodResolutionResult resolveMethodOnClassLegacy(
      DexType holder, DexMethodSignature signature) {
    assert checkIfObsolete();
    return resolveMethodOnClassLegacy(holder, signature.getProto(), signature.getName());
  }

  public MethodResolutionResult resolveMethodOnClassLegacy(
      DexType holder, DexProto proto, DexString name) {
    assert checkIfObsolete();
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .resolveMethodOnClass(holder, proto, name);
  }

  public MethodResolutionResult resolveMethodOnClassLegacy(DexClass clazz, DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnClassLegacy(clazz, method.getProto(), method.getName());
  }

  public MethodResolutionResult resolveMethodOnClassLegacy(
      DexClass clazz, DexMethodSignature signature) {
    assert checkIfObsolete();
    return resolveMethodOnClassLegacy(clazz, signature.getProto(), signature.getName());
  }

  public MethodResolutionResult resolveMethodOnClassLegacy(
      DexClass clazz, DexProto proto, DexString name) {
    assert checkIfObsolete();
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .resolveMethodOnClass(clazz, proto, name);
  }

  public MethodResolutionResult resolveMethodOnInterfaceHolderLegacy(DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnInterfaceLegacy(method.getHolderType(), method);
  }

  public MethodResolutionResult resolveMethodOnInterfaceLegacy(DexType holder, DexMethod method) {
    assert checkIfObsolete();
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .resolveMethodOnInterface(holder, method.getProto(), method.getName());
  }

  public MethodResolutionResult resolveMethodOnInterfaceLegacy(DexClass clazz, DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnInterfaceLegacy(clazz, method.getProto(), method.getName());
  }

  public MethodResolutionResult resolveMethodOnInterfaceLegacy(
      DexClass clazz, DexMethodSignature methodSignature) {
    assert checkIfObsolete();
    return resolveMethodOnInterfaceLegacy(
        clazz, methodSignature.getProto(), methodSignature.getName());
  }

  public MethodResolutionResult resolveMethodOnInterfaceLegacy(
      DexClass clazz, DexProto proto, DexString name) {
    assert checkIfObsolete();
    return MethodResolution.createLegacy(this::definitionFor, dexItemFactory())
        .resolveMethodOnInterface(clazz, proto, name);
  }

  /**
   * This method will query the definition of the holder to decide on which resolution to use.
   *
   * <p>This is to overcome the shortcoming of the DEX file format that does not allow to encode the
   * kind of a method reference.
   */
  public MethodResolutionResult unsafeResolveMethodDueToDexFormat(DexMethod method) {
    assert checkIfObsolete();
    return MethodResolution.create(
            this::contextIndependentDefinitionForWithResolutionResult, dexItemFactory())
        .unsafeResolveMethodDueToDexFormat(method);
  }

  public MethodResolutionResult resolveMethod(DexMethod invokedMethod, boolean isInterface) {
    assert checkIfObsolete();
    return resolveMethodOn(invokedMethod.getHolderType(), invokedMethod, isInterface);
  }

  public MethodResolutionResult resolveMethodOn(
      DexType holder, DexMethod method, boolean isInterface) {
    assert checkIfObsolete();
    return isInterface
        ? resolveMethodOnInterface(holder, method)
        : resolveMethodOnClass(holder, method);
  }

  public MethodResolutionResult resolveMethodOnClassHolder(DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnClass(method.getHolderType(), method);
  }

  public MethodResolutionResult resolveMethodOnClass(DexType holder, DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnClass(holder, method.getProto(), method.getName());
  }

  public MethodResolutionResult resolveMethodOnClass(DexType holder, DexMethodSignature signature) {
    assert checkIfObsolete();
    return resolveMethodOnClass(holder, signature.getProto(), signature.getName());
  }

  public MethodResolutionResult resolveMethodOnClass(
      DexType holder, DexProto proto, DexString name) {
    assert checkIfObsolete();
    return MethodResolution.create(
            this::contextIndependentDefinitionForWithResolutionResult, dexItemFactory())
        .resolveMethodOnClass(holder, proto, name);
  }

  public MethodResolutionResult resolveMethodOnClass(DexClass clazz, DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnClass(clazz, method.getProto(), method.getName());
  }

  public MethodResolutionResult resolveMethodOnClass(DexClass clazz, DexMethodSignature signature) {
    assert checkIfObsolete();
    return resolveMethodOnClass(clazz, signature.getProto(), signature.getName());
  }

  public MethodResolutionResult resolveMethodOnClass(
      DexClass clazz, DexProto proto, DexString name) {
    assert checkIfObsolete();
    return MethodResolution.create(
            this::contextIndependentDefinitionForWithResolutionResult, dexItemFactory())
        .resolveMethodOnClass(clazz, proto, name);
  }

  public MethodResolutionResult resolveMethodOnInterfaceHolder(DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnInterface(method.getHolderType(), method);
  }

  public MethodResolutionResult resolveMethodOnInterface(DexType holder, DexMethod method) {
    assert checkIfObsolete();
    return MethodResolution.create(
            this::contextIndependentDefinitionForWithResolutionResult, dexItemFactory())
        .resolveMethodOnInterface(holder, method.getProto(), method.getName());
  }

  public MethodResolutionResult resolveMethodOnInterface(DexClass clazz, DexMethod method) {
    assert checkIfObsolete();
    return resolveMethodOnInterface(clazz, method.getProto(), method.getName());
  }

  public MethodResolutionResult resolveMethodOnInterface(
      DexClass clazz, DexMethodSignature methodSignature) {
    assert checkIfObsolete();
    return resolveMethodOnInterface(clazz, methodSignature.getProto(), methodSignature.getName());
  }

  public MethodResolutionResult resolveMethodOnInterface(
      DexClass clazz, DexProto proto, DexString name) {
    assert checkIfObsolete();
    return MethodResolution.create(
            this::contextIndependentDefinitionForWithResolutionResult, dexItemFactory())
        .resolveMethodOnInterface(clazz, proto, name);
  }

  /**
   * Implements resolution of a field descriptor against the holder of the field. See also {@link
   * #resolveFieldOn}.
   */
  public FieldResolutionResult resolveField(DexField field) {
    assert checkIfObsolete();
    return resolveFieldOn(field.holder, field);
  }

  /** Intentionally drops {@param context} since this is only needed in D8. */
  @Override
  public FieldResolutionResult resolveFieldOn(DexType type, DexField field, ProgramMethod context) {
    assert checkIfObsolete();
    return resolveFieldOn(type, field);
  }

  // Keep as instance methods to ensure that one needs AppInfoWithClassHierarchy to do resolution.
  public FieldResolutionResult resolveFieldOn(DexType type, DexField field) {
    assert checkIfObsolete();
    return new FieldResolution(this).resolveFieldOn(type, field);
  }

  // Keep as instance methods to ensure that one needs AppInfoWithClassHierarchy to do resolution.
  public FieldResolutionResult resolveFieldOn(DexClass clazz, DexField field) {
    assert checkIfObsolete();
    return new FieldResolution(this).resolveFieldOn(clazz, field);
  }
}
