// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.lightir.LirOpcodes;

public enum NumberConversionType {
  INT_TO_BYTE(NumericType.INT, NumericType.BYTE, org.objectweb.asm.Opcodes.I2B),
  INT_TO_CHAR(NumericType.INT, NumericType.CHAR, org.objectweb.asm.Opcodes.I2C),
  INT_TO_SHORT(NumericType.INT, NumericType.SHORT, org.objectweb.asm.Opcodes.I2S),
  INT_TO_LONG(NumericType.INT, NumericType.LONG, org.objectweb.asm.Opcodes.I2L),
  INT_TO_FLOAT(NumericType.INT, NumericType.FLOAT, org.objectweb.asm.Opcodes.I2F),
  INT_TO_DOUBLE(NumericType.INT, NumericType.DOUBLE, org.objectweb.asm.Opcodes.I2D),
  LONG_TO_INT(NumericType.LONG, NumericType.INT, org.objectweb.asm.Opcodes.L2I),
  LONG_TO_FLOAT(NumericType.LONG, NumericType.FLOAT, org.objectweb.asm.Opcodes.L2F),
  LONG_TO_DOUBLE(NumericType.LONG, NumericType.DOUBLE, org.objectweb.asm.Opcodes.L2D),
  FLOAT_TO_INT(NumericType.FLOAT, NumericType.INT, org.objectweb.asm.Opcodes.F2I),
  FLOAT_TO_LONG(NumericType.FLOAT, NumericType.LONG, org.objectweb.asm.Opcodes.F2L),
  FLOAT_TO_DOUBLE(NumericType.FLOAT, NumericType.DOUBLE, org.objectweb.asm.Opcodes.F2D),
  DOUBLE_TO_INT(NumericType.DOUBLE, NumericType.INT, org.objectweb.asm.Opcodes.D2I),
  DOUBLE_TO_LONG(NumericType.DOUBLE, NumericType.LONG, org.objectweb.asm.Opcodes.D2L),
  DOUBLE_TO_FLOAT(NumericType.DOUBLE, NumericType.FLOAT, org.objectweb.asm.Opcodes.D2F);

  private final NumericType from;
  private final NumericType to;
  private final int asmOpcode;

  public NumericType getFrom() {
    return from;
  }

  public NumericType getTo() {
    return to;
  }

  public int getAsmOpcode() {
    return asmOpcode;
  }

  NumberConversionType(NumericType from, NumericType to, int asmOpcode) {
    this.from = from;
    this.to = to;
    this.asmOpcode = asmOpcode;
  }

  public static NumberConversionType fromAsmOpcode(int asmOpcode) {
    switch (asmOpcode) {
      case org.objectweb.asm.Opcodes.I2L:
        return INT_TO_LONG;
      case org.objectweb.asm.Opcodes.I2F:
        return INT_TO_FLOAT;
      case org.objectweb.asm.Opcodes.I2D:
        return INT_TO_DOUBLE;
      case org.objectweb.asm.Opcodes.L2I:
        return LONG_TO_INT;
      case org.objectweb.asm.Opcodes.L2F:
        return LONG_TO_FLOAT;
      case org.objectweb.asm.Opcodes.L2D:
        return LONG_TO_DOUBLE;
      case org.objectweb.asm.Opcodes.F2I:
        return FLOAT_TO_INT;
      case org.objectweb.asm.Opcodes.F2L:
        return FLOAT_TO_LONG;
      case org.objectweb.asm.Opcodes.F2D:
        return FLOAT_TO_DOUBLE;
      case org.objectweb.asm.Opcodes.D2I:
        return DOUBLE_TO_INT;
      case org.objectweb.asm.Opcodes.D2L:
        return DOUBLE_TO_LONG;
      case org.objectweb.asm.Opcodes.D2F:
        return DOUBLE_TO_FLOAT;
      case org.objectweb.asm.Opcodes.I2B:
        return INT_TO_BYTE;
      case org.objectweb.asm.Opcodes.I2C:
        return INT_TO_CHAR;
      case org.objectweb.asm.Opcodes.I2S:
        return INT_TO_SHORT;
      default:
        throw new Unreachable("Unexpected number conversion opcode " + asmOpcode);
    }
  }

  public static NumberConversionType fromLirOpcode(int lirOpcode) {
    switch (lirOpcode) {
      case LirOpcodes.I2L:
        return INT_TO_LONG;
      case LirOpcodes.I2F:
        return INT_TO_FLOAT;
      case LirOpcodes.I2D:
        return INT_TO_DOUBLE;
      case LirOpcodes.L2I:
        return LONG_TO_INT;
      case LirOpcodes.L2F:
        return LONG_TO_FLOAT;
      case LirOpcodes.L2D:
        return LONG_TO_DOUBLE;
      case LirOpcodes.F2I:
        return FLOAT_TO_INT;
      case LirOpcodes.F2L:
        return FLOAT_TO_LONG;
      case LirOpcodes.F2D:
        return FLOAT_TO_DOUBLE;
      case LirOpcodes.D2I:
        return DOUBLE_TO_INT;
      case LirOpcodes.D2L:
        return DOUBLE_TO_LONG;
      case LirOpcodes.D2F:
        return DOUBLE_TO_FLOAT;
      case LirOpcodes.I2B:
        return INT_TO_BYTE;
      case LirOpcodes.I2C:
        return INT_TO_CHAR;
      case LirOpcodes.I2S:
        return INT_TO_SHORT;
      default:
        throw new Unreachable("Unexpected number conversion LIR opcode " + lirOpcode);
    }
  }
}
