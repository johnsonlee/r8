// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.optimize.outliner.exceptions.ThrowBlockOutline;
import com.android.tools.r8.lightir.LirBuilder;

public class ThrowBlockOutlineMarker extends Instruction {

  private final ThrowBlockOutline outline;

  public ThrowBlockOutlineMarker(ThrowBlockOutline outline) {
    super(null);
    this.outline = outline;
  }

  public static Builder builder() {
    return new Builder();
  }

  public ThrowBlockOutline getOutline() {
    return outline;
  }

  @Override
  public DeadInstructionResult canBeDeadCode(AppView<?> appView, IRCode code) {
    return DeadInstructionResult.notDead();
  }

  @Override
  public int opcode() {
    return Opcodes.THROW_BLOCK_OUTLINE_MARKER;
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
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addThrowBlockOutlineMarker(outline);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    throw new Unreachable();
  }

  @Override
  public void insertLoadAndStores(LoadStoreHelper helper) {
    throw new Unreachable();
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public boolean isThrowBlockOutlineMarker() {
    return true;
  }

  @Override
  public ThrowBlockOutlineMarker asThrowBlockOutlineMarker() {
    return this;
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
  public String toString() {
    return "ThrowBlockOutlineMarker";
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return false;
  }

  public static class Builder extends BuilderBase<Builder, ThrowBlockOutlineMarker> {

    private ThrowBlockOutline outline;

    public Builder setOutline(ThrowBlockOutline outline) {
      this.outline = outline;
      return this;
    }

    @Override
    public ThrowBlockOutlineMarker build() {
      return amend(new ThrowBlockOutlineMarker(outline));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
