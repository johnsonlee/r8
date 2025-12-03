// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

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

  /**
   * @throws IllegalArgumentException for invalid conversions.
   */
  public static NumberConversionType fromTypes(NumericType from, NumericType to) {
    switch (from) {
      case INT:
        switch (to) {
          case BYTE:
            return INT_TO_BYTE;
          case CHAR:
            return INT_TO_CHAR;
          case SHORT:
            return INT_TO_SHORT;
          case LONG:
            return INT_TO_LONG;
          case FLOAT:
            return INT_TO_FLOAT;
          case DOUBLE:
            return INT_TO_DOUBLE;
          default:
            break;
        }
        break;
      case LONG:
        switch (to) {
          case INT:
            return LONG_TO_INT;
          case FLOAT:
            return LONG_TO_FLOAT;
          case DOUBLE:
            return LONG_TO_DOUBLE;
          default:
            break;
        }
        break;
      case FLOAT:
        switch (to) {
          case INT:
            return FLOAT_TO_INT;
          case LONG:
            return FLOAT_TO_LONG;
          case DOUBLE:
            return FLOAT_TO_DOUBLE;
          default:
            break;
        }
        break;
      case DOUBLE:
        switch (to) {
          case INT:
            return DOUBLE_TO_INT;
          case LONG:
            return DOUBLE_TO_LONG;
          case FLOAT:
            return DOUBLE_TO_FLOAT;
          default:
            break;
        }
        break;
      default:
        break;
    }
    throw new IllegalArgumentException(from + " to " + to + " is not a valid number conversion.");
  }
}
