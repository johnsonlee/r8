// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeUtils;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.StatefulObjectValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class NewArrayEmpty extends Instruction {

  public final DexType type;

  public NewArrayEmpty(Value dest, Value size, DexType type) {
    super(dest, size);
    this.type = type;
  }

  public DexType getArrayType() {
    return type;
  }

  @Override
  public int opcode() {
    return Opcodes.NEW_ARRAY_EMPTY;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public String toString() {
    return super.toString() + " " + type.toString();
  }

  public Value size() {
    return inValues.get(0);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int size = builder.allocatedRegister(size(), getNumber());
    int dest = builder.allocatedRegister(outValue, getNumber());
    builder.add(this, new DexNewArray(dest, size, type));
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U4BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U4BIT_MAX;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    // new-array throws if its size is negative, but can also potentially throw on out-of-memory.
    return true;
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    assert type.isArrayType();
    return isArrayTypeInaccessible(appView, context) || isArraySizeMaybeNegative();
  }

  private boolean isArrayTypeInaccessible(AppView<?> appView, ProgramMethod context) {
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    return !DexTypeUtils.isTypeAccessibleInMethodContext(appView, baseType, context);
  }

  private boolean isArraySizeMaybeNegative() {
    return sizeIfConst() < 0;
  }

  private ObjectState internalComputeObjectState(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (!instructionMayHaveSideEffects(appView, context, abstractValueSupplier)
        && size().getType().isInt()) {
      assert !instructionInstanceCanThrow(appView, context, abstractValueSupplier);
      return appView
          .abstractValueFactory()
          .createKnownLengthArrayState(size().definition.asConstNumber().getIntValue());
    }
    return ObjectState.empty();
  }

  @Override
  public ObjectState computeObjectState(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier) {
    return internalComputeObjectState(appView, context, abstractValueSupplier);
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    return StatefulObjectValue.create(
        internalComputeObjectState(appView, context, abstractValueSupplier));
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    if (instructionInstanceCanThrow(appView, code.context())) {
      return DeadInstructionResult.notDead();
    }
    return DeadInstructionResult.deadIfOutValueIsDead();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isNewArrayEmpty() && other.asNewArrayEmpty().type == type;
  }

  @Override
  public boolean isNewArrayEmpty() {
    return true;
  }

  @Override
  public NewArrayEmpty asNewArrayEmpty() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forNewArrayEmpty(type, context);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return type;
  }

  @Override
  public void insertLoadAndStores(LoadStoreHelper helper) {
    helper.loadInValues(this);
    helper.storeOutValue(this);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    assert type.isArrayType();
    builder.add(new CfNewArray(type), this);
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.fromDexType(type, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addNewArrayEmpty(size(), type);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerTypeReference(type);
  }

  // Returns the size of the array if it is known, -1 otherwise.
  public int sizeIfConst() {
    Value size = size();
    return size.isConstNumber() ? size.getConstInstruction().asConstNumber().getIntValue() : -1;
  }
}
