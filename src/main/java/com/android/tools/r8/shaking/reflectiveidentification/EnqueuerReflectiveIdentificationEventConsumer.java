// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.reflectiveidentification;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.KeepReason;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.WorkList;
import java.util.Collection;
import java.util.Set;

public class EnqueuerReflectiveIdentificationEventConsumer
    implements ReflectiveIdentificationEventConsumer {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Enqueuer enqueuer;
  private final InternalOptions options;

  public EnqueuerReflectiveIdentificationEventConsumer(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.enqueuer = enqueuer;
    this.options = appView.options();
  }

  @Override
  public void onJavaLangClassForName(DexClass clazz, ProgramMethod context) {
    if (clazz.isProgramClass()) {
      DexProgramClass programClass = clazz.asProgramClass();
      if (appView.allMergedClasses().isMergeSource(programClass.getType())) {
        return;
      }
      KeepReason reason = KeepReason.reflectiveUseIn(context);
      enqueuer.markTypeAsLive(programClass, reason);
      if (programClass.canBeInstantiatedByNewInstance()
          && options.isForceProguardCompatibilityEnabled()) {
        enqueuer.markClassAsInstantiatedWithCompatRule(programClass, () -> reason);
      } else {
        enqueuer.markDirectAndIndirectClassInitializersAsLive(programClass);
      }
      // To ensure we are not moving the class because we cannot prune it when there is a reflective
      // use of it.
      if (enqueuer.getKeepInfo().getClassInfo(programClass).isShrinkingAllowed(options)) {
        enqueuer
            .getKeepInfo()
            .joinClass(programClass, joiner -> joiner.disallowOptimization().disallowShrinking());
      }
    } else {
      enqueuer.recordNonProgramClassWithNoMissingReporting(clazz, context);
    }
  }

  @Override
  public void onJavaLangClassNewInstance(DexProgramClass clazz, ProgramMethod context) {
    ProgramMethod defaultInitializer = clazz.getProgramDefaultInitializer();
    if (defaultInitializer != null) {
      enqueuer.traceReflectiveNewInstance(defaultInitializer, context);
    }
  }

  @Override
  public void onJavaLangReflectConstructorNewInstance(
      ProgramMethod initializer, ProgramMethod context) {
    enqueuer.traceReflectiveNewInstance(initializer, context);
  }

  @Override
  public void onJavaLangReflectProxyNewProxyInstance(
      Set<DexProgramClass> classes, ProgramMethod context) {
    KeepReason reason = KeepReason.reflectiveUseIn(context);
    for (DexProgramClass clazz : classes) {
      enqueuer.markInterfaceAsInstantiated(
          clazz, enqueuer.getGraphReporter().registerClass(clazz, reason));
    }

    WorkList<DexProgramClass> worklist = WorkList.newIdentityWorkList(classes);
    while (worklist.hasNext()) {
      DexProgramClass clazz = worklist.next();
      assert clazz.isInterface();

      // Keep this interface to ensure that we do not merge the interface into its unique subtype,
      // or merge other interfaces into it horizontally.
      enqueuer
          .getKeepInfo()
          .joinClass(clazz, joiner -> joiner.disallowOptimization().disallowShrinking());

      // Also keep all of its virtual methods to ensure that the devirtualizer does not perform
      // illegal rewritings of invoke-interface instructions into invoke-virtual instructions.
      if (enqueuer.getMode().isInitialTreeShaking()) {
        clazz.forEachProgramVirtualMethod(
            virtualMethod -> {
              enqueuer
                  .getKeepInfo()
                  .joinMethod(
                      virtualMethod, joiner -> joiner.disallowOptimization().disallowShrinking());
              enqueuer.markVirtualMethodAsReachable(
                  virtualMethod.getReference(), true, context, reason);
            });
      }

      // Repeat for all super interfaces.
      for (DexType implementedType : clazz.getInterfaces()) {
        DexProgramClass implementedClass =
            asProgramClassOrNull(enqueuer.definitionFor(implementedType, clazz));
        if (implementedClass != null && implementedClass.isInterface()) {
          worklist.addIfNotSeen(implementedClass);
        }
      }
    }
  }

  @Override
  public void onJavaUtilServiceLoaderLoad(
      DexProgramClass serviceClass,
      Collection<DexProgramClass> implementationClasses,
      ProgramMethod context) {
    if (serviceClass != null && !serviceClass.isPublic()) {
      // Package-private service types are allowed only when the load() call is made from the same
      // package.
      enqueuer.applyMinimumKeepInfoWhenLive(
          serviceClass,
          KeepClassInfo.newEmptyJoiner()
              .disallowHorizontalClassMerging()
              .disallowVerticalClassMerging()
              .disallowAccessModification());
    }

    KeepReason reason = KeepReason.reflectiveUseIn(context);
    for (DexProgramClass implementationClass : implementationClasses) {
      enqueuer.markClassAsInstantiatedWithReason(implementationClass, reason);
      ProgramMethod defaultInitializer = implementationClass.getProgramDefaultInitializer();
      if (defaultInitializer != null) {
        enqueuer.applyMinimumKeepInfoWhenLiveOrTargeted(
            defaultInitializer, KeepMethodInfo.newEmptyJoiner().disallowOptimization());
      }
    }
  }
}
