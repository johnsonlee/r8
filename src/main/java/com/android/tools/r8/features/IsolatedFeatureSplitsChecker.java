// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.features;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
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

  @SuppressWarnings("UnusedVariable")
  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  @SuppressWarnings("UnusedVariable")
  private final Enqueuer enqueuer;

  private IsolatedFeatureSplitsChecker(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.enqueuer = enqueuer;
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    if (enabled(appView, enqueuer)) {
      IsolatedFeatureSplitsChecker checker = new IsolatedFeatureSplitsChecker(appView, enqueuer);
      enqueuer
          .registerFieldAccessAnalysis(checker)
          .registerInvokeAnalysis(checker)
          .registerTypeAccessAnalysis(checker);
    }
  }

  private static boolean enabled(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    InternalOptions options = appView.options();
    return options.hasFeatureSplitConfiguration()
        && options.getFeatureSplitConfiguration().isIsolatedSplitsEnabled()
        && enqueuer.getMode().isInitialTreeShaking();
  }

  @SuppressWarnings("UnusedVariable")
  private void traceFieldAccess(FieldResolutionResult resolutionResult, ProgramMethod context) {
    // TODO(b/300247439): Check access.
  }

  @SuppressWarnings("UnusedVariable")
  private void traceMethodInvoke(MethodResolutionResult resolutionResult, ProgramMethod context) {
    // TODO(b/300247439): Check access.
  }

  @SuppressWarnings("UnusedVariable")
  private void traceTypeAccess(DexClass clazz, ProgramMethod context) {
    // TODO(b/300247439): Check access.
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
