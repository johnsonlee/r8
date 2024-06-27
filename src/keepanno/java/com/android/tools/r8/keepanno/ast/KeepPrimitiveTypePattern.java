// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.TypePatternPrimitive;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;

public class KeepPrimitiveTypePattern {

  private static final KeepPrimitiveTypePattern ANY = new KeepPrimitiveTypePattern('*');
  private static final KeepPrimitiveTypePattern BOOLEAN = new KeepPrimitiveTypePattern('Z');
  private static final KeepPrimitiveTypePattern BYTE = new KeepPrimitiveTypePattern('B');
  private static final KeepPrimitiveTypePattern CHAR = new KeepPrimitiveTypePattern('C');
  private static final KeepPrimitiveTypePattern SHORT = new KeepPrimitiveTypePattern('S');
  private static final KeepPrimitiveTypePattern INT = new KeepPrimitiveTypePattern('I');
  private static final KeepPrimitiveTypePattern LONG = new KeepPrimitiveTypePattern('J');
  private static final KeepPrimitiveTypePattern FLOAT = new KeepPrimitiveTypePattern('F');
  private static final KeepPrimitiveTypePattern DOUBLE = new KeepPrimitiveTypePattern('D');

  private static final Map<String, KeepPrimitiveTypePattern> PRIMITIVES =
      populate(BOOLEAN, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);

  private static ImmutableMap<String, KeepPrimitiveTypePattern> populate(
      KeepPrimitiveTypePattern... types) {
    ImmutableMap.Builder<String, KeepPrimitiveTypePattern> builder = ImmutableMap.builder();
    for (KeepPrimitiveTypePattern type : types) {
      builder.put(type.getDescriptor(), type);
    }
    return builder.build();
  }

  public static KeepPrimitiveTypePattern getAny() {
    return ANY;
  }

  public static KeepPrimitiveTypePattern getBoolean() {
    return BOOLEAN;
  }

  public static KeepPrimitiveTypePattern getByte() {
    return BYTE;
  }

  public static KeepPrimitiveTypePattern getChar() {
    return CHAR;
  }

  public static KeepPrimitiveTypePattern getShort() {
    return SHORT;
  }

  public static KeepPrimitiveTypePattern getInt() {
    return INT;
  }

  public static KeepPrimitiveTypePattern getLong() {
    return LONG;
  }

  public static KeepPrimitiveTypePattern getFloat() {
    return FLOAT;
  }

  public static KeepPrimitiveTypePattern getDouble() {
    return DOUBLE;
  }

  private final char descriptor;

  public KeepPrimitiveTypePattern(char descriptor) {
    this.descriptor = descriptor;
  }

  public boolean isAny() {
    return this == ANY;
  }

  public char getDescriptorChar() {
    if (isAny()) {
      throw new KeepEdgeException("No descriptor exists for 'any' primitive");
    }
    return descriptor;
  }

  public String getDescriptor() {
    return Character.toString(getDescriptorChar());
  }

  public static void forEachPrimitive(Consumer<KeepPrimitiveTypePattern> fn) {
    PRIMITIVES.values().forEach(fn);
  }

  public static KeepPrimitiveTypePattern fromProto(TypePatternPrimitive primitive) {
    switch (primitive.getNumber()) {
      case TypePatternPrimitive.PRIMITIVE_BOOLEAN_VALUE:
        return getBoolean();
      case TypePatternPrimitive.PRIMITIVE_BYTE_VALUE:
        return getByte();
      case TypePatternPrimitive.PRIMITIVE_CHAR_VALUE:
        return getChar();
      case TypePatternPrimitive.PRIMITIVE_SHORT_VALUE:
        return getShort();
      case TypePatternPrimitive.PRIMITIVE_INT_VALUE:
        return getInt();
      case TypePatternPrimitive.PRIMITIVE_LONG_VALUE:
        return getLong();
      case TypePatternPrimitive.PRIMITIVE_FLOAT_VALUE:
        return getFloat();
      case TypePatternPrimitive.PRIMITIVE_DOUBLE_VALUE:
        return getDouble();
      default:
        return getAny();
    }
  }

  public TypePatternPrimitive buildProto() {
    if (this == BOOLEAN) {
      return TypePatternPrimitive.PRIMITIVE_BOOLEAN;
    }
    if (this == BYTE) {
      return TypePatternPrimitive.PRIMITIVE_BYTE;
    }
    if (this == CHAR) {
      return TypePatternPrimitive.PRIMITIVE_CHAR;
    }
    if (this == SHORT) {
      return TypePatternPrimitive.PRIMITIVE_SHORT;
    }
    if (this == INT) {
      return TypePatternPrimitive.PRIMITIVE_INT;
    }
    if (this == LONG) {
      return TypePatternPrimitive.PRIMITIVE_LONG;
    }
    if (this == FLOAT) {
      return TypePatternPrimitive.PRIMITIVE_FLOAT;
    }
    if (this == DOUBLE) {
      return TypePatternPrimitive.PRIMITIVE_DOUBLE;
    }
    assert isAny();
    return TypePatternPrimitive.PRIMITIVE_UNSPECIFIED;
  }
}
