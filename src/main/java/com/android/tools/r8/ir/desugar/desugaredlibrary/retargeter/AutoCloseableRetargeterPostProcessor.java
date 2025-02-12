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
import java.util.Collection;
import java.util.Collections;
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
      ensureInterfacesAndForwardingMethodsSynthesized(programClasses, eventConsumer);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void ensureInterfacesAndForwardingMethodsSynthesized(
      Collection<DexProgramClass> programClasses,
      AutoCloseableRetargeterPostProcessingEventConsumer eventConsumer) {
    ProcessorContext processorContext = appView.createProcessorContext();
    MainThreadContext mainThreadContext = processorContext.createMainThreadContext();
    for (DexProgramClass clazz : programClasses) {
      if (clazz.superType == null) {
        assert clazz.type == appView.dexItemFactory().objectType : clazz.type.toSourceString();
        continue;
      }
      if (implementsAutoCloseableAtLibraryBoundary(clazz)) {
        ensureInterfacesAndForwardingMethodsSynthesized(eventConsumer, clazz, mainThreadContext);
      }
    }
  }

  private boolean implementsAutoCloseableAtLibraryBoundary(DexProgramClass clazz) {
    if (clazz.interfaces.contains(appView.dexItemFactory().autoCloseableType)) {
      return true;
    }
    WorkList<DexType> workList = collectLibrarySuperTypeAndInterfaces(clazz);
    return libraryTypesImplementsAutoCloseable(workList, clazz);
  }

  private WorkList<DexType> collectLibrarySuperTypeAndInterfaces(DexProgramClass clazz) {
    WorkList<DexType> workList = WorkList.newIdentityWorkList();
    DexClass superclass = appView.definitionFor(clazz.superType);
    // Only performs computation if superclass is a library class, but not object to filter out
    // the most common case.
    if (superclass != null
        && superclass.isLibraryClass()
        && !superclass.type.isIdenticalTo(appView.dexItemFactory().objectType)) {
      workList.addIfNotSeen(superclass.type);
    }
    for (DexType itf : clazz.interfaces) {
      DexClass superItf = appView.definitionFor(itf);
      if (superItf != null) {
        workList.addIfNotSeen(superItf.type);
      }
    }
    return workList;
  }

  private boolean libraryTypesImplementsAutoCloseable(
      WorkList<DexType> workList, DexProgramClass clazz) {
    while (workList.hasNext()) {
      DexType current = workList.next();
      if (current.isIdenticalTo(appView.dexItemFactory().objectType)) {
        continue;
      }
      DexClass currentClass = appView.definitionFor(current);
      if (currentClass == null) {
        reportInvalidSupertype(current, clazz);
        continue;
      }
      if (currentClass.interfaces.contains(appView.dexItemFactory().autoCloseableType)) {
        return true;
      }
      workList.addIfNotSeen(currentClass.superType);
      workList.addIfNotSeen(currentClass.interfaces);
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

  private void ensureInterfacesAndForwardingMethodsSynthesized(
      AutoCloseableRetargeterPostProcessingEventConsumer eventConsumer,
      DexProgramClass clazz,
      MainThreadContext mainThreadContext) {
    // DesugaredLibraryRetargeter emulate dispatch: insertion of a marker interface & forwarding
    // methods.
    // We cannot use the ClassProcessor since this applies up to 26, while the ClassProcessor
    // applies up to 24.
    if (appView.isAlreadyLibraryDesugared(clazz)) {
      return;
    }
    DexMethod close = getClose(clazz.type);
    if (clazz.lookupVirtualMethod(close) == null) {
      DexEncodedMethod newMethod =
          createForwardingMethod(close, clazz, eventConsumer, mainThreadContext);
      if (newMethod == null) {
        // We don't support desugaring on all subtypes, in which case there is not need to inject
        // the interface.
        return;
      }
      clazz.addVirtualMethod(newMethod);
      eventConsumer.acceptAutoCloseableForwardingMethod(new ProgramMethod(clazz, newMethod), clazz);
    }
    DexType autoCloseableType = appView.dexItemFactory().autoCloseableType;
    if (clazz.interfaces.contains(autoCloseableType)) {
      return;
    }
    clazz.addExtraInterfaces(
        Collections.singletonList(new ClassTypeSignature(autoCloseableType)),
        appView.dexItemFactory());
    eventConsumer.acceptAutoCloseableInterfaceInjection(
        clazz, appView.definitionFor(autoCloseableType));
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
    if (superMethod == null
        || !data.superTargetsToRewrite().contains(superMethod.getHolderType())) {
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
