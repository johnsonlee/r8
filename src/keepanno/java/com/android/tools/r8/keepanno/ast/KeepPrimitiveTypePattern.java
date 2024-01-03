// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

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
}
