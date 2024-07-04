// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.TypePatternArray;
import com.google.common.base.Strings;
import java.util.Objects;

public class KeepArrayTypePattern {

  private static final KeepArrayTypePattern ANY =
      new KeepArrayTypePattern(KeepTypePattern.any(), 1);

  public static KeepArrayTypePattern getAny() {
    return ANY;
  }

  private final KeepTypePattern baseType;
  private final int dimensions;

  public KeepArrayTypePattern(KeepTypePattern baseType, int dimensions) {
    assert baseType != null;
    assert dimensions > 0;
    this.baseType = baseType;
    this.dimensions = dimensions;
  }

  public boolean isAny() {
    return ANY.equals(this);
  }

  public KeepTypePattern getBaseType() {
    return baseType;
  }

  public int getDimensions() {
    return dimensions;
  }

  public String getDescriptor() {
    if (isAny()) {
      throw new KeepEdgeException("No descriptor exists for 'any' array");
    }
    return Strings.repeat("[", dimensions)
        + baseType.apply(
            () -> {
              throw new KeepEdgeException("No descriptor exists for 'any primitive' array");
            },
            KeepPrimitiveTypePattern::getDescriptor,
            array -> {
              throw new KeepEdgeException("Unexpected nested array");
            },
            clazz -> clazz.getClassNamePattern().getExactDescriptor());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepArrayTypePattern)) {
      return false;
    }
    KeepArrayTypePattern that = (KeepArrayTypePattern) o;
    return dimensions == that.dimensions && Objects.equals(baseType, that.baseType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseType, dimensions);
  }

  @Override
  public String toString() {
    return baseType + Strings.repeat("[]", dimensions);
  }

  public static KeepArrayTypePattern fromProto(TypePatternArray array) {
    KeepTypePattern baseType =
        array.hasBaseType()
            ? KeepTypePattern.fromProto(array.getBaseType())
            : KeepTypePattern.any();
    int dimensions = Math.max(1, array.getDimensions());
    return new KeepArrayTypePattern(baseType, dimensions);
  }

  public TypePatternArray.Builder buildProto() {
    return TypePatternArray.newBuilder()
        .setDimensions(dimensions)
        .setBaseType(baseType.buildProto());
  }
}
