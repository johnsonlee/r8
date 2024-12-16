// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;

public class StoreStoreFence extends Instruction {

  public StoreStoreFence(Value value) {
    super(null, value);
  }

  @Override
  public boolean isStoreStoreFence() {
    return true;
  }

  @Override
  public StoreStoreFence asStoreStoreFence() {
    return this;
  }

  @Override
  public int opcode() {
    return Opcodes.STORE_STORE_FENCE;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addStoreStoreFence(getFirstOperand());
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    return DeadInstructionResult.deadIfInValueIsDead(getFirstOperand());
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isStoreStoreFence();
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    return false;
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable();
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forStoreStoreFence();
  }

  @Override
  public void insertLoadAndStores(LoadStoreHelper helper) {
    throw new Unreachable();
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  void internalRegisterUse(UseRegistry<?> registry, DexClassAndMethod context) {
    registry.registerInvokeStatic(
        registry.dexItemFactory().javaLangInvokeVarHandleMembers.storeStoreFence);
  }
}
