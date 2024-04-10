// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.isApiSafeForMemberRebinding;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistry;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.TriConsumer;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

public class MemberRebindingAnalysis {

  private final AndroidApiLevelCompute androidApiLevelCompute;
  private final AppView<AppInfoWithLiveness> appView;
  private final MemberRebindingEventConsumer eventConsumer;
  private final InternalOptions options;

  private final MemberRebindingLens.Builder lensBuilder;

  public MemberRebindingAnalysis(AppView<AppInfoWithLiveness> appView) {
    assert appView.graphLens().isContextFreeForMethods(appView.codeLens());
    this.androidApiLevelCompute = appView.apiLevelCompute();
    this.appView = appView;
    this.eventConsumer = MemberRebindingEventConsumer.create(appView);
    this.options = appView.options();
    this.lensBuilder = MemberRebindingLens.builder(appView);
  }

  private DexMethod validMemberRebindingTargetForNonProgramMethod(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts,
      InvokeType invokeType,
      DexMethod original) {
    assert !resolvedMethod.isProgramMethod();

    if (invokeType.isDirect()) {
      return original;
    }

    if (invokeType.isSuper() && options.canHaveSuperInvokeBug()) {
      // To preserve semantics we should find the first library method on the boundary.
      DexType firstLibraryTarget =
          firstLibraryClassOrFirstInterfaceTarget(
              resolutionResult.getResolvedHolder(),
              appView,
              resolvedMethod.getReference(),
              original.getHolderType(),
              DexClass::lookupMethod);
      if (firstLibraryTarget == null) {
        return original;
      }
      DexClass libraryHolder = appView.definitionFor(firstLibraryTarget);
      if (libraryHolder == null) {
        return original;
      }
      if (libraryHolder == resolvedMethod.getHolder()) {
        return resolvedMethod.getReference();
      }
      return resolvedMethod.getReference().withHolder(libraryHolder, appView.dexItemFactory());
    }

    LibraryMethod eligibleLibraryMethod = null;
    SingleResolutionResult<?> currentResolutionResult = resolutionResult;
    while (currentResolutionResult != null) {
      DexClassAndMethod currentResolvedMethod = currentResolutionResult.getResolutionPair();
      if (canRebindDirectlyToLibraryMethod(
          currentResolvedMethod,
          currentResolutionResult.withInitialResolutionHolder(
              currentResolutionResult.getResolvedHolder()),
          contexts,
          invokeType,
          original)) {
        eligibleLibraryMethod = currentResolvedMethod.asLibraryMethod();
      }
      if (appView.getAssumeInfoCollection().contains(currentResolvedMethod)) {
        break;
      }
      DexClass currentResolvedHolder = currentResolvedMethod.getHolder();
      if (resolvedMethod.getDefinition().belongsToVirtualPool()
          && !currentResolvedHolder.isInterface()
          && currentResolvedHolder.getSuperType() != null) {
        currentResolutionResult =
            appView
                .appInfo()
                .resolveMethodOnClassLegacy(currentResolvedHolder.getSuperType(), original)
                .asSingleResolution();
      } else {
        break;
      }
    }
    if (eligibleLibraryMethod != null) {
      return eligibleLibraryMethod.getReference();
    }

    DexType newHolder =
        firstLibraryClassOrFirstInterfaceTarget(
            resolvedMethod.getHolder(),
            appView,
            resolvedMethod.getReference(),
            original.getHolderType(),
            DexClass::lookupMethod);
    return newHolder != null ? original.withHolder(newHolder, appView.dexItemFactory()) : original;
  }

  private boolean canRebindDirectlyToLibraryMethod(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts,
      InvokeType invokeType,
      DexMethod original) {
    // TODO(b/194422791): It could potentially be that `original.holder` is not a subtype of
    //  `original.holder` on all API levels, in which case it is not OK to rebind to the resolved
    //  method.
    return resolvedMethod.isLibraryMethod()
        && isAccessibleInAllContexts(resolvedMethod, resolutionResult, contexts)
        && !isInvokeSuperToInterfaceMethod(resolvedMethod, invokeType)
        && !isInvokeSuperToAbstractMethod(resolvedMethod, invokeType)
        && isApiSafeForMemberRebinding(
            resolvedMethod.asLibraryMethod(), original, androidApiLevelCompute, options);
  }

  private boolean isAccessibleInAllContexts(
      DexClassAndMethod resolvedMethod,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethodSet contexts) {
    if (resolvedMethod.getHolder().isPublic() && resolvedMethod.getDefinition().isPublic()) {
      return true;
    }
    return Iterables.all(
        contexts,
        context -> resolutionResult.isAccessibleFrom(context, appView, appView.appInfo()).isTrue());
  }

  private boolean isInvokeSuperToInterfaceMethod(DexClassAndMethod method, InvokeType invokeType) {
    return method.getHolder().isInterface() && invokeType.isSuper();
  }

  private boolean isInvokeSuperToAbstractMethod(DexClassAndMethod method, InvokeType invokeType) {
    return method.getAccessFlags().isAbstract() && invokeType.isSuper();
  }

  public static DexField validMemberRebindingTargetFor(
      DexDefinitionSupplier definitions, DexClassAndField field, DexField original) {
    if (field.isProgramField()) {
      return field.getReference();
    }
    DexClass fieldHolder = field.getHolder();
    DexType newHolder =
        firstLibraryClassOrFirstInterfaceTarget(
            fieldHolder,
            definitions,
            field.getReference(),
            original.getHolderType(),
            DexClass::lookupField);
    return newHolder != null
        ? original.withHolder(newHolder, definitions.dexItemFactory())
        : original;
  }

  private static <T> DexType firstLibraryClassOrFirstInterfaceTarget(
      DexClass holder,
      DexDefinitionSupplier definitions,
      T target,
      DexType current,
      BiFunction<DexClass, T, ?> lookup) {
    return holder.isInterface()
        ? firstLibraryClassForInterfaceTarget(definitions, target, current, lookup)
        : firstLibraryClass(definitions, current);
  }

  private static <T> DexType firstLibraryClassForInterfaceTarget(
      DexDefinitionSupplier definitions,
      T target,
      DexType current,
      BiFunction<DexClass, T, ?> lookup) {
    DexClass clazz = definitions.definitionFor(current);
    if (clazz == null) {
      return null;
    }
    Object potential = lookup.apply(clazz, target);
    if (potential != null) {
      // Found, return type.
      return current;
    }
    if (clazz.superType != null) {
      DexType matchingSuper =
          firstLibraryClassForInterfaceTarget(definitions, target, clazz.superType, lookup);
      if (matchingSuper != null) {
        // Found in supertype, return first library class.
        return clazz.isNotProgramClass() ? current : matchingSuper;
      }
    }
    for (DexType iface : clazz.getInterfaces()) {
      DexType matchingIface =
          firstLibraryClassForInterfaceTarget(definitions, target, iface, lookup);
      if (matchingIface != null) {
        // Found in interface, return first library class.
        return clazz.isNotProgramClass() ? current : matchingIface;
      }
    }
    return null;
  }

  private static DexType firstLibraryClass(DexDefinitionSupplier definitions, DexType bottom) {
    DexClass searchClass = definitions.contextIndependentDefinitionFor(bottom);
    while (searchClass != null && searchClass.isProgramClass()) {
      searchClass =
          definitions.definitionFor(searchClass.getSuperType(), searchClass.asProgramClass());
    }
    return searchClass != null ? searchClass.getType() : null;
  }

  private Map<InvokeType, NonReboundMethodAccessCollection>
      computeNonReboundMethodAccessCollections(ExecutorService executorService)
          throws ExecutionException {
    Map<InvokeType, NonReboundMethodAccessCollection> nonReboundMethodAccessCollections =
        new ConcurrentHashMap<>();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          Map<InvokeType, NonReboundMethodAccessCollection> classResult =
              computeNonReboundMethodAccessCollections(clazz);
          classResult.forEach(
              (invokeType, nonReboundMethodAccessCollection) ->
                  nonReboundMethodAccessCollections
                      .computeIfAbsent(
                          invokeType, ignoreKey(NonReboundMethodAccessCollection::createConcurrent))
                      .mergeThreadLocalIntoShared(nonReboundMethodAccessCollection));
        },
        options.getThreadingModule(),
        executorService);
    return nonReboundMethodAccessCollections;
  }

  private Map<InvokeType, NonReboundMethodAccessCollection>
      computeNonReboundMethodAccessCollections(DexProgramClass clazz) {
    Map<InvokeType, NonReboundMethodAccessCollection> nonReboundMethodAccessCollections =
        new IdentityHashMap<>();
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method ->
            method.registerCodeReferences(
                new DefaultUseRegistry<>(appView, method) {

                  private final AppView<AppInfoWithLiveness> appViewWithLiveness =
                      MemberRebindingAnalysis.this.appView;

                  @Override
                  public void registerInvokeDirect(DexMethod method) {
                    // Intentionally empty.
                  }

                  @Override
                  public void registerInvokeInterface(DexMethod method) {
                    registerInvoke(
                        InvokeType.INTERFACE,
                        method,
                        appViewWithLiveness
                            .appInfo()
                            .resolveMethodOnInterface(method.getHolderType(), method)
                            .asSingleResolution());
                  }

                  @Override
                  public void registerInvokeStatic(DexMethod method) {
                    registerInvoke(
                        InvokeType.STATIC,
                        method,
                        appViewWithLiveness
                            .appInfo()
                            .unsafeResolveMethodDueToDexFormat(method)
                            .asSingleResolution());
                  }

                  @Override
                  public void registerInvokeSuper(DexMethod method) {
                    registerInvoke(
                        InvokeType.SUPER,
                        method,
                        appViewWithLiveness
                            .appInfo()
                            .unsafeResolveMethodDueToDexFormat(method)
                            .asSingleResolution());
                  }

                  @Override
                  public void registerInvokeVirtual(DexMethod method) {
                    registerInvoke(
                        InvokeType.VIRTUAL,
                        method,
                        appViewWithLiveness
                            .appInfo()
                            .resolveMethodOnClassHolder(method)
                            .asSingleResolution());
                  }

                  private void registerInvoke(
                      InvokeType invokeType,
                      DexMethod method,
                      SingleResolutionResult<?> resolutionResult) {
                    if (resolutionResult != null
                        && resolutionResult
                            .getResolvedMethod()
                            .getReference()
                            .isNotIdenticalTo(method)) {
                      nonReboundMethodAccessCollections
                          .computeIfAbsent(
                              invokeType, ignoreKey(NonReboundMethodAccessCollection::create))
                          .add(method, resolutionResult, getContext());
                    }
                  }
                }));
    return nonReboundMethodAccessCollections;
  }

  private void computeMethodRebinding(
      Map<InvokeType, NonReboundMethodAccessCollection> nonReboundMethodAccessCollections) {
    nonReboundMethodAccessCollections.forEach(this::computeMethodRebinding);
  }

  private void computeMethodRebinding(
      InvokeType invokeType, NonReboundMethodAccessCollection nonReboundMethodAccessCollection) {
    Map<DexProgramClass, List<Pair<DexMethod, DexClassAndMethod>>> bridges =
        new IdentityHashMap<>();
    TriConsumer<DexProgramClass, DexMethod, DexClassAndMethod> addBridge =
        (bridgeHolder, method, target) ->
            bridges
                .computeIfAbsent(bridgeHolder, k -> new ArrayList<>())
                .add(new Pair<>(method, target));

    nonReboundMethodAccessCollection.forEach(
        (method, resolutionResult, contexts) -> {
          // TODO(b/128404854) Rebind to the lowest library class or program class. For now we allow
          //  searching in library for methods, but this should be done on classpath instead.
          DexClassAndMethod resolvedMethod = resolutionResult.getResolutionPair();
          DexClass initialResolutionHolder = resolutionResult.getInitialResolutionHolder();
          DexMethod bridgeMethod = null;
          if (initialResolutionHolder.isProgramClass()) {
            // In Java bytecode, it is only possible to target interface methods that are in one of
            // the immediate super-interfaces via a super-invocation (see
            // IndirectSuperInterfaceTest).
            // To avoid introducing an IncompatibleClassChangeError at runtime we therefore insert a
            // bridge method when we are about to rebind to an interface method that is not the
            // original target.
            if (needsBridgeForInterfaceMethod(
                initialResolutionHolder, resolvedMethod, invokeType)) {
              bridgeMethod =
                  insertBridgeForInterfaceMethod(
                      method, resolvedMethod, initialResolutionHolder.asProgramClass(), addBridge);
            } else {
              // If the target class is not public but the targeted method is, we might run into
              // visibility problems when rebinding.
              if (contexts.stream()
                  .anyMatch(context -> mayNeedBridgeForVisibility(context, resolutionResult))) {
                bridgeMethod =
                    insertBridgeForVisibilityIfNeeded(
                        method, resolvedMethod, initialResolutionHolder, addBridge);
              }
            }
          }
          if (bridgeMethod != null) {
            lensBuilder.map(method, bridgeMethod, invokeType);
          } else if (resolvedMethod.isProgramMethod()) {
            lensBuilder.map(method, resolvedMethod.getReference(), invokeType);
          } else {
            lensBuilder.map(
                method,
                validMemberRebindingTargetForNonProgramMethod(
                    resolvedMethod,
                    resolutionResult.asSingleResolution(),
                    contexts,
                    invokeType,
                    method),
                invokeType);
          }
        });

    bridges.forEach(
        (bridgeHolder, targets) -> {
          // Sorting the list of bridges within a class maintains a deterministic order of entries
          // in the method collection.
          targets.sort(Comparator.comparing(Pair::getFirst));
          for (Pair<DexMethod, DexClassAndMethod> pair : targets) {
            DexMethod method = pair.getFirst();
            DexClassAndMethod target = pair.getSecond();
            DexMethod bridgeMethod =
                method.withHolder(bridgeHolder.getType(), appView.dexItemFactory());
            if (bridgeHolder.getMethodCollection().getMethod(bridgeMethod) == null) {
              DexEncodedMethod targetDefinition = target.getDefinition();
              DexEncodedMethod bridgeMethodDefinition =
                  targetDefinition.toForwardingMethod(
                      bridgeHolder,
                      appView,
                      builder -> {
                        if (!targetDefinition.isAbstract()
                            && targetDefinition.getApiLevelForCode().isNotSetApiLevel()) {
                          assert target.isLibraryMethod()
                              || !appView.options().apiModelingOptions().enableLibraryApiModeling;
                          builder.setApiLevelForCode(
                              appView
                                  .apiLevelCompute()
                                  .computeApiLevelForLibraryReference(
                                      targetDefinition.getReference(),
                                      appView.computedMinApiLevel()));
                        }
                        builder.setIsLibraryMethodOverrideIf(
                            // Treat classpath override as library override.
                            target.isLibraryMethod() || target.isClasspathMethod(),
                            OptionalBool.TRUE);
                      });
              assert !bridgeMethodDefinition.belongsToVirtualPool()
                  || !bridgeMethodDefinition.isLibraryMethodOverride().isUnknown();
              bridgeHolder.addMethod(bridgeMethodDefinition);
              if (!appView.options().debug) {
                // TODO(b/333677610): Register these methods in debug mode as well.
                appView
                    .getKeepInfo()
                    .registerCompilerSynthesizedMethod(bridgeMethodDefinition.getReference());
              }
              eventConsumer.acceptMemberRebindingBridgeMethod(
                  bridgeMethodDefinition.asProgramMethod(bridgeHolder), target);
            }
            assert appView
                .appInfo()
                .unsafeResolveMethodDueToDexFormat(method)
                .getResolvedMethod()
                .getReference()
                .isIdenticalTo(bridgeMethod);
          }
        });
  }

  private boolean needsBridgeForInterfaceMethod(
      DexClass originalClass, DexClassAndMethod method, InvokeType invokeType) {
    return options.isGeneratingClassFiles()
        && invokeType == InvokeType.SUPER
        && method.getHolder() != originalClass
        && method.getHolder().isInterface();
  }

  private DexMethod insertBridgeForInterfaceMethod(
      DexMethod method,
      DexClassAndMethod target,
      DexProgramClass originalClass,
      TriConsumer<DexProgramClass, DexMethod, DexClassAndMethod> bridges) {
    // If `targetClass` is a class, then insert the bridge method on the upper-most super class that
    // implements the interface. Otherwise, if it is an interface, then insert the bridge method
    // directly on the interface (because that interface must be the immediate super type, assuming
    // that the super-invocation is not broken in advance).
    //
    // Note that, to support compiling from DEX to CF, we would need to rewrite the targets of
    // invoke-super instructions that hit indirect interface methods such that they always target
    // a method in an immediate super-interface, since this works on Art but not on the JVM.
    DexProgramClass bridgeHolder =
        findHolderForInterfaceMethodBridge(originalClass, target.getHolderType());
    assert bridgeHolder != null;
    assert bridgeHolder != target.getHolder();
    bridges.accept(bridgeHolder, method, target);
    return target.getReference().withHolder(bridgeHolder.getType(), appView.dexItemFactory());
  }

  private DexProgramClass findHolderForInterfaceMethodBridge(DexProgramClass clazz, DexType iface) {
    if (clazz.accessFlags.isInterface()) {
      return clazz;
    }
    DexClass superClass = appView.definitionFor(clazz.superType);
    if (superClass == null
        || superClass.isNotProgramClass()
        || !appView.appInfo().isSubtype(superClass.type, iface)) {
      return clazz;
    }
    return findHolderForInterfaceMethodBridge(superClass.asProgramClass(), iface);
  }

  private boolean mayNeedBridgeForVisibility(
      ProgramMethod context, SingleResolutionResult<?> resolutionResult) {
    return resolutionResult.isAccessibleFrom(context, appView).isTrue()
        && AccessControl.isClassAccessible(resolutionResult.getResolvedHolder(), context, appView)
            .isPossiblyFalse();
  }

  private DexMethod insertBridgeForVisibilityIfNeeded(
      DexMethod method,
      DexClassAndMethod target,
      DexClass originalClass,
      TriConsumer<DexProgramClass, DexMethod, DexClassAndMethod> bridges) {
    // If the original class is public and this method is public, it might have been called
    // from anywhere, so we need a bridge. Likewise, if the original is in a different
    // package, we might need a bridge, too.
    String packageDescriptor =
        originalClass.accessFlags.isPublic() ? null : method.holder.getPackageDescriptor();
    if (packageDescriptor == null
        || !packageDescriptor.equals(target.getHolderType().getPackageDescriptor())) {
      DexProgramClass bridgeHolder =
          findHolderForVisibilityBridge(originalClass, target.getHolder(), packageDescriptor);
      assert bridgeHolder != null;
      bridges.accept(bridgeHolder, method, target);
      return target.getReference().withHolder(bridgeHolder.getType(), appView.dexItemFactory());
    }
    return target.getReference();
  }

  private DexProgramClass findHolderForVisibilityBridge(
      DexClass originalClass, DexClass targetClass, String packageDescriptor) {
    if (originalClass == targetClass || originalClass.isNotProgramClass()) {
      return null;
    }
    DexProgramClass newHolder = null;
    // Recurse through supertype chain.
    if (appView.appInfo().isSubtype(originalClass.superType, targetClass.type)) {
      DexClass superClass = appView.definitionFor(originalClass.superType);
      newHolder = findHolderForVisibilityBridge(superClass, targetClass, packageDescriptor);
    } else {
      for (DexType iface : originalClass.interfaces.values) {
        if (appView.appInfo().isSubtype(iface, targetClass.type)) {
          DexClass interfaceClass = appView.definitionFor(iface);
          newHolder = findHolderForVisibilityBridge(interfaceClass, targetClass, packageDescriptor);
        }
      }
    }
    if (newHolder != null) {
      // A supertype fulfills the visibility requirements.
      return newHolder;
    } else if (originalClass.accessFlags.isPublic()
        || originalClass.type.getPackageDescriptor().equals(packageDescriptor)) {
      // This class is visible. Return it if it is a program class, otherwise null.
      return originalClass.asProgramClass();
    }
    return null;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    AppInfoWithLiveness appInfo = appView.appInfo();
    Map<InvokeType, NonReboundMethodAccessCollection> nonReboundMethodAccessCollections =
        computeNonReboundMethodAccessCollections(executorService);
    computeMethodRebinding(nonReboundMethodAccessCollections);
    appInfo.getFieldAccessInfoCollection().flattenAccessContexts();
    MemberRebindingLens memberRebindingLens = lensBuilder.build();
    appView.setGraphLens(memberRebindingLens);
    eventConsumer.finished(appView, memberRebindingLens);
    appView.dexItemFactory().clearTypeElementsCache();
    appView.notifyOptimizationFinishedForTesting();
  }

  static class NonReboundMethodAccessCollection {

    private final Map<DexMethod, NonReboundMethodAccessInfo> nonReboundMethodReferences;

    private NonReboundMethodAccessCollection(
        Map<DexMethod, NonReboundMethodAccessInfo> nonReboundMethodReferences) {
      this.nonReboundMethodReferences = nonReboundMethodReferences;
    }

    public static NonReboundMethodAccessCollection create() {
      return new NonReboundMethodAccessCollection(new IdentityHashMap<>());
    }

    public static NonReboundMethodAccessCollection createConcurrent() {
      return new NonReboundMethodAccessCollection(new ConcurrentHashMap<>());
    }

    public void add(
        DexMethod method, SingleResolutionResult<?> resolutionResult, ProgramMethod context) {
      nonReboundMethodReferences
          .computeIfAbsent(
              method, ignoreKey(() -> NonReboundMethodAccessInfo.create(resolutionResult)))
          .addContext(context);
    }

    public void forEach(
        TriConsumer<DexMethod, SingleResolutionResult<?>, ProgramMethodSet> consumer) {
      nonReboundMethodReferences.forEach(
          (reference, info) -> consumer.accept(reference, info.resolutionResult, info.contexts));
    }

    public void mergeThreadLocalIntoShared(
        NonReboundMethodAccessCollection nonReboundMethodAccessCollection) {
      nonReboundMethodAccessCollection.forEach(
          (method, resolutionResult, contexts) ->
              nonReboundMethodReferences
                  .computeIfAbsent(
                      method,
                      ignoreKey(
                          () -> NonReboundMethodAccessInfo.createConcurrent(resolutionResult)))
                  .addContexts(contexts));
    }
  }

  static class NonReboundMethodAccessInfo {

    private final SingleResolutionResult<?> resolutionResult;
    private final ProgramMethodSet contexts;

    NonReboundMethodAccessInfo(
        SingleResolutionResult<?> resolutionResult, ProgramMethodSet contexts) {
      this.resolutionResult = resolutionResult;
      this.contexts = contexts;
    }

    public static NonReboundMethodAccessInfo create(SingleResolutionResult<?> resolutionResult) {
      return new NonReboundMethodAccessInfo(resolutionResult, ProgramMethodSet.create());
    }

    public static NonReboundMethodAccessInfo createConcurrent(
        SingleResolutionResult<?> resolutionResult) {
      return new NonReboundMethodAccessInfo(resolutionResult, ProgramMethodSet.createConcurrent());
    }

    public void addContext(ProgramMethod context) {
      this.contexts.add(context);
    }

    public void addContexts(ProgramMethodSet contexts) {
      this.contexts.addAll(contexts);
    }
  }
}
