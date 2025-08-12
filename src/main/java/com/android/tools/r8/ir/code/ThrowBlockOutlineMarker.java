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
import com.android.tools.r8.utils.ListUtils;
import java.util.List;

public class ThrowBlockOutlineMarker extends Instruction {

  private final ThrowBlockOutline outline;

  public ThrowBlockOutlineMarker(ThrowBlockOutline outline, List<Value> arguments) {
    super(null, arguments);
    this.outline = outline;
  }

  public static Builder builder() {
    return new Builder();
  }

  // Removes the in-values from this outline marker where the corresponding outline parameter has
  // been removed due to constant propagation.
  public boolean detachConstantOutlineArguments(ThrowBlockOutline outline) {
    List<Value> newArguments =
        ListUtils.mapOrElse(
            inValues,
            (i, argument) -> {
              if (outline.isArgumentConstant(i)) {
                argument.removeUser(this);
                return null;
              }
              return argument;
            },
            null);
    if (newArguments != null) {
      inValues.clear();
      inValues.addAll(newArguments);
      return true;
    }
    return false;
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
    builder.addThrowBlockOutlineMarker(outline, inValues);
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

    private List<Value> arguments;
    private ThrowBlockOutline outline;

    public Builder setArguments(List<Value> arguments) {
      this.arguments = arguments;
      return this;
    }

    public Builder setOutline(ThrowBlockOutline outline) {
      this.outline = outline;
      return this;
    }

    @Override
    public ThrowBlockOutlineMarker build() {
      return amend(new ThrowBlockOutlineMarker(outline, arguments));
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
