// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.AutoCloseableRetargeterHelper.lookupSuperIncludingInterfaces;

import com.android.tools.r8.contexts.CompilationContext.MainThreadContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.AutoCloseableRetargeterEventConsumer.AutoCloseableRetargeterPostProcessingEventConsumer;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

// The rewrite of virtual calls requires to go through emulate dispatch. This class is responsible
// for inserting interfaces on library boundaries and forwarding methods in the program, and to
// synthesize the interfaces and emulated dispatch classes in the desugared library.
public class AutoCloseableRetargeterPostProcessor implements CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final AutoCloseableRetargeterHelper data;

  public AutoCloseableRetargeterPostProcessor(AppView<?> appView) {
    this.appView = appView;
    this.data =
        new AutoCloseableRetargeterHelper(
            appView.options().getMinApiLevel(), appView.dexItemFactory());
  }

  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService,
      Timing timing) {
    try (Timing t0 = timing.begin("Auto closeable retargeter post processor")) {
      ProcessorContext processorContext = appView.createProcessorContext();
      MainThreadContext mainThreadContext = processorContext.createMainThreadContext();
      List<ProgramMethod> bridges = new ArrayList<>();
      for (DexProgramClass clazz : programClasses) {
        if (clazz.superType == null) {
          assert clazz.type.isIdenticalTo(appView.dexItemFactory().objectType)
              : clazz.type.toSourceString();
          continue;
        }
        if (clazz.isInterface() && appView.options().isInterfaceMethodDesugaringEnabled()) {
          // We cannot add default methods on interfaces if interface method desugaring is enabled.
          continue;
        }
        if (implementsAutoCloseableAtLibraryBoundary(clazz)) {
          ProgramMethod bridge =
              ensureInterfacesAndForwardingMethodsSynthesized(
                  eventConsumer, clazz, mainThreadContext);
          if (bridge != null) {
            bridges.add(bridge);
          }
        }
      }
      // We add the bridges in the end so they don't interfer with lookups in between.
      for (ProgramMethod bridge : bridges) {
        bridge.getHolder().addVirtualMethod(bridge.getDefinition());
        eventConsumer.acceptAutoCloseableForwardingMethod(bridge, bridge.getHolder());
      }
    }
  }

  private boolean implementsAutoCloseableAtLibraryBoundary(DexProgramClass clazz) {
    if (clazz.interfaces.contains(appView.dexItemFactory().autoCloseableType)) {
      return true;
    }
    WorkList<DexClass> workList = collectLibrarySuperTypeAndInterfaces(clazz);
    return libraryTypesImplementsAutoCloseable(workList, clazz);
  }

  private WorkList<DexClass> collectLibrarySuperTypeAndInterfaces(DexProgramClass clazz) {
    WorkList<DexClass> workList = WorkList.newIdentityWorkList();
    DexClass superclass = appView.definitionFor(clazz.superType);
    // R8 only performs the computation if the superclass is a library class.
    if (superclass != null && superclass.isLibraryClass()) {
      workList.addIfNotSeen(superclass);
    }
    for (DexType itf : clazz.interfaces) {
      DexClass superItf = appView.definitionFor(itf);
      if (superItf != null) {
        // R8 only performs the computation if the super interface is a library class. If interface
        // method desugaring is enabled, we need to go through all interfaces to insert the bridges
        // on the classes and not the interfaces.
        if (appView.options().isInterfaceMethodDesugaringEnabled() || superItf.isLibraryClass()) {
          workList.addIfNotSeen(superItf);
        }
      }
    }
    return workList;
  }

  private boolean libraryTypesImplementsAutoCloseable(
      WorkList<DexClass> workList, DexProgramClass clazz) {
    while (workList.hasNext()) {
      DexClass current = workList.next();
      if (current.getType().isIdenticalTo(appView.dexItemFactory().objectType)) {
        continue;
      }
      if (current.interfaces.contains(appView.dexItemFactory().autoCloseableType)) {
        return true;
      }
      DexClass superClass = appView.definitionFor(current.superType);
      if (superClass == null) {
        reportInvalidSupertype(current.superType, clazz);
      } else {
        workList.addIfNotSeen(superClass);
      }
      for (DexType itf : current.interfaces) {
        DexClass superItf = appView.definitionFor(itf);
        if (superItf != null) {
          workList.addIfNotSeen(superItf);
        }
      }
    }
    return false;
  }

  private void reportInvalidSupertype(DexType current, DexProgramClass origin) {
    appView
        .options()
        .warningInvalidLibrarySuperclassForDesugar(
            origin.getOrigin(),
            origin.type,
            current,
            "missing",
            ImmutableSet.of(getClose(origin.type)));
  }

  private ProgramMethod ensureInterfacesAndForwardingMethodsSynthesized(
      AutoCloseableRetargeterPostProcessingEventConsumer eventConsumer,
      DexProgramClass clazz,
      MainThreadContext mainThreadContext) {
    // DesugaredLibraryRetargeter emulate dispatch: insertion of a marker interface & forwarding
    // methods.
    // We cannot use the ClassProcessor since this applies up to 26, while the ClassProcessor
    // applies up to 24.
    if (appView.isAlreadyLibraryDesugared(clazz)) {
      return null;
    }
    ProgramMethod bridge = null;
    DexMethod close = getClose(clazz.type);
    if (clazz.lookupVirtualMethod(close) == null) {
      DexEncodedMethod newMethod =
          createForwardingMethod(close, clazz, eventConsumer, mainThreadContext);
      if (newMethod == null) {
        // We don't support desugaring on all subtypes, in which case there is not need to inject
        // the interface.
        return bridge;
      }
      bridge = new ProgramMethod(clazz, newMethod);
    }
    DexType autoCloseableType = appView.dexItemFactory().autoCloseableType;
    if (clazz.interfaces.contains(autoCloseableType)) {
      return bridge;
    }
    clazz.addExtraInterfaces(
        Collections.singletonList(new ClassTypeSignature(autoCloseableType)),
        appView.dexItemFactory());
    eventConsumer.acceptAutoCloseableInterfaceInjection(
        clazz, appView.definitionFor(autoCloseableType));
    return bridge;
  }

  @SuppressWarnings("ReferenceEquality")
  private DexEncodedMethod createForwardingMethod(
      DexMethod target,
      DexProgramClass clazz,
      AutoCloseableRetargeterPostProcessingEventConsumer eventConsumer,
      MainThreadContext mainThreadContext) {
    // NOTE: Never add a forwarding method to methods of classes unknown or coming from android.jar
    // even if this results in invalid code, these classes are never desugared.
    // In desugared library, emulated interface methods can be overridden by retarget lib members.
    AppInfoWithClassHierarchy appInfoForDesugaring = appView.appInfoForDesugaring();
    assert clazz.lookupVirtualMethod(target) == null;
    DexClassAndMethod superMethod = lookupSuperIncludingInterfaces(appView, target, clazz);
    if (superMethod == null || !data.isSuperTargetToRewrite(superMethod.getHolderType())) {
      return null;
    }
    DexMethod forwardMethod =
        data.synthesizeDispatchCase(
            appView,
            superMethod.getHolderType(),
            clazz,
            eventConsumer,
            () -> mainThreadContext.createUniqueContext(clazz));
    assert forwardMethod != null && forwardMethod != target;
    DexClassAndMethod resolvedMethod =
        appInfoForDesugaring
            .resolveMethodLegacy(target, target.getHolderType().isInterface(appInfoForDesugaring))
            .getResolutionPair();
    assert resolvedMethod != null;
    DexEncodedMethod desugaringForwardingMethod =
        DexEncodedMethod.createDesugaringForwardingMethod(
            resolvedMethod,
            clazz,
            forwardMethod,
            appView.dexItemFactory(),
            appView.getSyntheticItems().isSynthetic(forwardMethod.getHolderType()));
    desugaringForwardingMethod.setLibraryMethodOverride(OptionalBool.TRUE);
    return desugaringForwardingMethod;
  }

  private DexMethod getClose(DexType holder) {
    return appView
        .dexItemFactory()
        .createMethod(
            holder,
            appView.dexItemFactory().createProto(appView.dexItemFactory().voidType),
            "close");
  }
}
