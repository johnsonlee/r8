// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

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
}
