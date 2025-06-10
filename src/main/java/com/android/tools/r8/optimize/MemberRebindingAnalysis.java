// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistry;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.TriConsumer;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class MemberRebindingAnalysis extends MemberRebindingHelper {

  private final MemberRebindingEventConsumer eventConsumer;

  private final MemberRebindingLens.Builder lensBuilder;

  public MemberRebindingAnalysis(AppView<AppInfoWithLiveness> appView) {
    super(appView);
    assert appView.graphLens().isContextFreeForMethods(appView.codeLens());
    this.eventConsumer = MemberRebindingEventConsumer.create(appView);
    this.lensBuilder = MemberRebindingLens.builder(appView);
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
            DexMethod bridgeMethodReference =
                method.withHolder(bridgeHolder.getType(), appView.dexItemFactory());
            if (bridgeHolder.getMethodCollection().getMethod(bridgeMethodReference) == null) {
              DexEncodedMethod targetDefinition = target.getDefinition();
              DexEncodedMethod bridgeMethodDefinition =
                  targetDefinition.toForwardingMethod(
                      bridgeHolder,
                      appView,
                      builder -> {
                        if (!targetDefinition.isAbstract()
                            && targetDefinition.getApiLevelForCode().isNotSetApiLevel()) {
                          assert target.isLibraryMethod()
                              || !appView.options().apiModelingOptions().isApiModelingEnabled();
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
              ProgramMethod bridgeMethod = bridgeMethodDefinition.asProgramMethod(bridgeHolder);
              appView.getKeepInfo().registerCompilerSynthesizedMethod(bridgeMethod);
              eventConsumer.acceptMemberRebindingBridgeMethod(bridgeMethod, target);
            }
            assert appView
                .appInfo()
                .unsafeResolveMethodDueToDexFormat(method)
                .getResolvedMethod()
                .getReference()
                .isIdenticalTo(bridgeMethodReference);
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
    Map<InvokeType, NonReboundMethodAccessCollection> nonReboundMethodAccessCollections =
        computeNonReboundMethodAccessCollections(executorService);
    computeMethodRebinding(nonReboundMethodAccessCollections);
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
