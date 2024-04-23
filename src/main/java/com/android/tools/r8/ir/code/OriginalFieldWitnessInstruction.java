// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.OriginalFieldWitness;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;

public class OriginalFieldWitnessInstruction extends Move {

  private final OriginalFieldWitness witness;

  public OriginalFieldWitnessInstruction(
      OriginalFieldWitness witness, Value outValue, Value inValue) {
    super(outValue, inValue);
    this.witness = witness;
  }

  public OriginalFieldWitness getWitness() {
    return witness;
  }

  @Override
  public int opcode() {
    return Opcodes.ORIGINAL_FIELD_WITNESS;
  }

  @Override
  public boolean isOriginalFieldWitness() {
    return true;
  }

  @Override
  public OriginalFieldWitnessInstruction asOriginalFieldWitness() {
    return this;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addOriginalFieldWitness(getWitness(), src());
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (this == other) {
      return true;
    }
    if (!other.isOriginalFieldWitness()) {
      return false;
    }
    OriginalFieldWitnessInstruction o = other.asOriginalFieldWitness();
    return witness.isEqualTo(o.witness);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable("We never write out witness instructions");
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("We never write out witness instructions");
  }
}
