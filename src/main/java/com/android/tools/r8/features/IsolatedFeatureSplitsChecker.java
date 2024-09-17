// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.features;

import com.android.tools.r8.errors.dontwarn.DontWarnConfiguration;
import com.android.tools.r8.features.diagnostic.IllegalAccessWithIsolatedFeatureSplitsDiagnostic;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerFieldAccessAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerInvokeAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerTypeAccessAnalysis;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.utils.InternalOptions;

// TODO(b/300247439): Also trace types referenced from new-array instructions, call sites, etc.
public class IsolatedFeatureSplitsChecker
    implements EnqueuerFieldAccessAnalysis, EnqueuerInvokeAnalysis, EnqueuerTypeAccessAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ClassToFeatureSplitMap features;

  private IsolatedFeatureSplitsChecker(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.features = appView.appInfo().getClassToFeatureSplitMap();
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    if (enabled(appView)) {
      IsolatedFeatureSplitsChecker checker = new IsolatedFeatureSplitsChecker(appView);
      enqueuer
          .registerFieldAccessAnalysis(checker)
          .registerInvokeAnalysis(checker)
          .registerTypeAccessAnalysis(checker);
    }
  }

  private static boolean enabled(AppView<? extends AppInfoWithClassHierarchy> appView) {
    InternalOptions options = appView.options();
    return options.hasFeatureSplitConfiguration()
        && options.getFeatureSplitConfiguration().isIsolatedSplitsEnabled();
  }

  private void traceFieldAccess(FieldResolutionResult resolutionResult, ProgramMethod context) {
    ProgramField resolvedField = resolutionResult.getSingleProgramField();
    if (resolvedField != null) {
      checkAccess(resolvedField, context);
      checkAccess(resolutionResult.getInitialResolutionHolder().asProgramClass(), context);
    }
  }

  private void traceMethodInvoke(MethodResolutionResult resolutionResult, ProgramMethod context) {
    ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
    if (resolvedMethod != null) {
      checkAccess(resolvedMethod, context);
      checkAccess(resolutionResult.getInitialResolutionHolder().asProgramClass(), context);
    }
  }

  private void traceTypeAccess(DexClass clazz, ProgramMethod context) {
    if (clazz != null && clazz.isProgramClass()) {
      checkAccess(clazz.asProgramClass(), context);
    }
  }

  private void checkAccess(ProgramDefinition accessedItem, ProgramMethod context) {
    if (accessedItem.getAccessFlags().isPublic()
        || features.isInSameFeature(accessedItem, context, appView)) {
      return;
    }
    if (accessedItem.getAccessFlags().isProtected()
        && appView.appInfo().isSubtype(context.getContextClass(), accessedItem.getContextClass())) {
      return;
    }
    DontWarnConfiguration dontWarnConfiguration = appView.getDontWarnConfiguration();
    if (dontWarnConfiguration.matches(accessedItem) || dontWarnConfiguration.matches(context)) {
      return;
    }
    appView
        .reporter()
        .error(new IllegalAccessWithIsolatedFeatureSplitsDiagnostic(accessedItem, context));
  }

  // Field accesses.

  @Override
  public void traceInstanceFieldRead(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    traceFieldAccess(resolutionResult, context);
  }

  @Override
  public void traceInstanceFieldWrite(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    traceFieldAccess(resolutionResult, context);
  }

  @Override
  public void traceStaticFieldRead(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    traceFieldAccess(resolutionResult, context);
  }

  @Override
  public void traceStaticFieldWrite(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    traceFieldAccess(resolutionResult, context);
  }

  // Method invokes.

  @Override
  public void traceInvokeStatic(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    traceMethodInvoke(resolutionResult, context);
  }

  @Override
  public void traceInvokeDirect(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    traceMethodInvoke(resolutionResult, context);
  }

  @Override
  public void traceInvokeInterface(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    traceMethodInvoke(resolutionResult, context);
  }

  @Override
  public void traceInvokeSuper(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    traceMethodInvoke(resolutionResult, context);
  }

  @Override
  public void traceInvokeVirtual(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    traceMethodInvoke(resolutionResult, context);
  }

  // Type accesses.

  @Override
  public void traceCheckCast(DexType type, DexClass clazz, ProgramMethod context) {
    traceTypeAccess(clazz, context);
  }

  @Override
  public void traceSafeCheckCast(DexType type, DexClass clazz, ProgramMethod context) {
    traceTypeAccess(clazz, context);
  }

  @Override
  public void traceConstClass(DexType type, DexClass clazz, ProgramMethod context) {
    traceTypeAccess(clazz, context);
  }

  @Override
  public void traceExceptionGuard(DexType type, DexClass clazz, ProgramMethod context) {
    traceTypeAccess(clazz, context);
  }

  @Override
  public void traceInstanceOf(DexType type, DexClass clazz, ProgramMethod context) {
    traceTypeAccess(clazz, context);
  }

  @Override
  public void traceNewInstance(DexType type, DexClass clazz, ProgramMethod context) {
    traceTypeAccess(clazz, context);
  }
}
