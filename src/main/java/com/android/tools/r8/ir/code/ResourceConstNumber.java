// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;

/**
 * Instruction representing the SSA value an R class field value.
 *
 * <p>This instruction allows us to correctly trace inlined resource values in the second round of
 * tree shaking.
 *
 * <p>The instruction is simple converted back to a const number before writing.
 */
public class ResourceConstNumber extends ConstInstruction {

  private final int value;

  public ResourceConstNumber(Value dest, int value) {
    super(dest);
    assert dest.type.isPrimitiveType();
    this.value = value;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int opcode() {
    return Opcodes.RESOURCE_CONST_NUMBER;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean instructionTypeCanBeCanonicalized() {
    return true;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("We never write out ResourceConstNumber");
  }

  @Override
  public void insertLoadAndStores(LoadStoreHelper helper) {
    throw new Unreachable("We never write cf code with resource numbers");
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable("We never write out a resource const number");
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable("We map out of ResourceConstNumber before register allocation");
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable("We map out of ResourceConstNumber before register allocation");
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (other == this) {
      return true;
    }
    if (!other.isResourceConstNumber()) {
      return false;
    }
    ResourceConstNumber otherNumber = other.asResourceConstNumber();
    return otherNumber.getValue() == getValue();
  }

  @Override
  public boolean isOutConstant() {
    return true;
  }

  @Override
  public boolean isResourceConstNumber() {
    return true;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    assert getOutType().isInt();
    return TypeElement.getInt();
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (outValue.hasLocalInfo()) {
      return AbstractValue.unknown();
    }
    return appView.abstractValueFactory().createSingleResourceNumberValue(getValue());
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addResourceConstNumber(getValue());
  }

  public static ResourceConstNumber copyOf(IRCode code, ResourceConstNumber original) {
    Value newValue = code.createValue(TypeElement.getInt(), original.getLocalInfo());
    return new ResourceConstNumber(newValue, original.getValue());
  }

  @Override
  public ResourceConstNumber asResourceConstNumber() {
    return this;
  }

  public int getValue() {
    return value;
  }

  public static class Builder extends BuilderBase<Builder, ResourceConstNumber> {

    private int value;

    public Builder setValue(int value) {
      this.value = value;
      return this;
    }

    @Override
    public ResourceConstNumber build() {
      return amend(new ResourceConstNumber(outValue, value));
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    protected boolean verifyInstructionTypeCannotThrow() {
      return true;
    }
  }
}
