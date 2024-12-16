// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.features.FeatureSplitBoundaryOptimizationUtils;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

// Computes the inlining constraint for a given instruction.
public class InliningConstraints {

  private AppView<AppInfoWithLiveness> appView;

  public InliningConstraints(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public AppView<AppInfoWithLiveness> getAppView() {
    return appView;
  }

  public ConstraintWithTarget forAlwaysMaterializingUser() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forArgument() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forArrayGet() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forArrayLength() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forArrayPut() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forBinop() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDexItemBasedConstString(DexReference type, ProgramMethod context) {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forCheckCast(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forConstClass(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forConstInstruction() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDebugLocalRead() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDebugLocalsChange() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDebugPosition() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDup() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forDup2() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forInitClass(DexType clazz, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, clazz, appView);
  }

  public ConstraintWithTarget forInstanceGet(DexField field, ProgramMethod context) {
    return forFieldInstruction(field, context);
  }

  public ConstraintWithTarget forInstanceOf(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forInstancePut(DexField field, ProgramMethod context) {
    return forFieldInstruction(field, context);
  }

  public ConstraintWithTarget forInvoke(DexMethod method, InvokeType type, ProgramMethod context) {
    switch (type) {
      case DIRECT:
        return forInvokeDirect(method, context);
      case INTERFACE:
        return forInvokeInterface(method, context);
      case STATIC:
        return forInvokeStatic(method, context);
      case SUPER:
        return forInvokeSuper(method, context);
      case VIRTUAL:
        return forInvokeVirtual(method, context);
      case CUSTOM:
        return forInvokeCustom();
      case POLYMORPHIC:
        return forInvokePolymorphic(method, context);
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  public ConstraintWithTarget forInvokeCustom() {
    // TODO(b/135965362): Test and support inlining invoke dynamic.
    return ConstraintWithTarget.NEVER;
  }

  public ConstraintWithTarget forInvokeDirect(DexMethod method, ProgramMethod context) {
    if (method.holder.isArrayType()) {
      return ConstraintWithTarget.ALWAYS;
    }
    SingleResolutionResult<?> resolutionResult =
        appView.appInfo().unsafeResolveMethodDueToDexFormatLegacy(method).asSingleResolution();
    if (resolutionResult == null) {
      return ConstraintWithTarget.NEVER;
    }
    DexClassAndMethod target =
        resolutionResult.lookupInvokeDirectTarget(context.getHolder(), appView);
    if (target == null) {
      return ConstraintWithTarget.NEVER;
    }
    return forResolvedMember(resolutionResult.getInitialResolutionHolder(), context, target);
  }

  public ConstraintWithTarget forInvokeInterface(DexMethod method, ProgramMethod context) {
    return forVirtualInvoke(method, context, true);
  }

  public ConstraintWithTarget forInvokeMultiNewArray(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forNewArrayFilled(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forInvokePolymorphic(DexMethod method, ProgramMethod context) {
    return ConstraintWithTarget.NEVER;
  }

  public ConstraintWithTarget forInvokeStatic(DexMethod method, ProgramMethod context) {
    if (method.holder.isArrayType()) {
      return ConstraintWithTarget.ALWAYS;
    }
    SingleResolutionResult<?> resolutionResult =
        appView.appInfo().unsafeResolveMethodDueToDexFormatLegacy(method).asSingleResolution();
    if (resolutionResult == null) {
      return ConstraintWithTarget.NEVER;
    }
    DexClassAndMethod target =
        resolutionResult.lookupInvokeStaticTarget(context.getHolder(), appView);
    if (target == null) {
      return ConstraintWithTarget.NEVER;
    }
    return forResolvedMember(resolutionResult.getInitialResolutionHolder(), context, target);
  }

  public ConstraintWithTarget forInvokeSuper(DexMethod method, ProgramMethod context) {
    // The semantics of invoke super depend on the context.
    return new ConstraintWithTarget(Constraint.SAMECLASS, context.getHolderType());
  }

  public ConstraintWithTarget forInvokeVirtual(DexMethod method, ProgramMethod context) {
    return forVirtualInvoke(method, context, false);
  }

  public ConstraintWithTarget forJumpInstruction() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forLoad() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forMonitor() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forMove() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forMoveException() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forNewArrayEmpty(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forRecordFieldValues() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forNewArrayFilledData() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forNewInstance(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.classIsVisible(context, type, appView);
  }

  public ConstraintWithTarget forNewUnboxedEnumInstance(DexType type, ProgramMethod context) {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forAssume() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forPop() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forReturn(TypeElement returnType, ProgramMethod context) {
    // If the return value is not an instance of the static return type, then do not inline.
    if (returnType.lessThanOrEqual(context.getReturnType().toTypeElement(appView), appView)) {
      return ConstraintWithTarget.ALWAYS;
    }
    return ConstraintWithTarget.NEVER;
  }

  public ConstraintWithTarget forReturnVoid() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forStaticGet(DexField field, ProgramMethod context) {
    return forFieldInstruction(field, context);
  }

  public ConstraintWithTarget forStaticPut(DexField field, ProgramMethod context) {
    return forFieldInstruction(field, context);
  }

  public ConstraintWithTarget forStore() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forStoreStoreFence() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forSwap() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forThrow() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forUnop() {
    return ConstraintWithTarget.ALWAYS;
  }

  public ConstraintWithTarget forConstMethodHandle() {
    return ConstraintWithTarget.NEVER;
  }

  public ConstraintWithTarget forConstMethodType() {
    return ConstraintWithTarget.NEVER;
  }

  private ConstraintWithTarget forFieldInstruction(DexField field, ProgramMethod context) {
    SingleFieldResolutionResult<?> fieldResolutionResult =
        appView.appInfo().resolveField(field).asSingleFieldResolutionResult();
    if (fieldResolutionResult == null) {
      return ConstraintWithTarget.NEVER;
    }
    return forResolvedMember(
        fieldResolutionResult.getInitialResolutionHolder(),
        context,
        fieldResolutionResult.getResolutionPair());
  }

  private ConstraintWithTarget forVirtualInvoke(
      DexMethod method, ProgramMethod context, boolean isInterface) {
    if (method.holder.isArrayType()) {
      return ConstraintWithTarget.ALWAYS;
    }

    // Perform resolution and derive inlining constraints based on the accessibility of the
    // resolution result.
    SingleResolutionResult<?> resolutionResult =
        appView.appInfo().resolveMethodLegacy(method, isInterface).asSingleResolution();
    if (resolutionResult == null || !resolutionResult.isVirtualTarget()) {
      return ConstraintWithTarget.NEVER;
    }
    return forResolvedMember(
        resolutionResult.getInitialResolutionHolder(),
        context,
        resolutionResult.getResolutionPair());
  }

  @SuppressWarnings("ReferenceEquality")
  private ConstraintWithTarget forResolvedMember(
      DexClass initialResolutionHolder,
      ProgramMethod context,
      DexClassAndMember<?, ?> resolvedMember) {
    if (!FeatureSplitBoundaryOptimizationUtils.isSafeForAccess(
            initialResolutionHolder, context, appView)
        || !FeatureSplitBoundaryOptimizationUtils.isSafeForAccess(
            resolvedMember, context, appView)) {
      return ConstraintWithTarget.NEVER;
    }
    DexType resolvedHolder = resolvedMember.getHolderType();
    assert initialResolutionHolder != null;
    ConstraintWithTarget memberConstraintWithTarget =
        ConstraintWithTarget.deriveConstraint(
            context, resolvedHolder, resolvedMember.getAccessFlags(), appView);
    // We also have to take the constraint of the initial resolution holder into account.
    ConstraintWithTarget classConstraintWithTarget =
        ConstraintWithTarget.deriveConstraint(
            context,
            initialResolutionHolder.getType(),
            initialResolutionHolder.getAccessFlags(),
            appView);
    return ConstraintWithTarget.meet(
        classConstraintWithTarget, memberConstraintWithTarget, appView);
  }
}
