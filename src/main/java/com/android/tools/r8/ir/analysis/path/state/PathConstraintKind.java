// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path.state;

public enum PathConstraintKind {
  POSITIVE,
  NEGATIVE,
  DISABLED;

  static PathConstraintKind get(boolean negate) {
    return negate ? NEGATIVE : POSITIVE;
  }

  boolean isNegation(PathConstraintKind other) {
    switch (this) {
      case POSITIVE:
        return other == NEGATIVE;
      case NEGATIVE:
        return other == POSITIVE;
      default:
        return false;
    }
  }

  PathConstraintKind join(PathConstraintKind other) {
    if (other == null) {
      return this;
    }
    return this == other ? this : DISABLED;
  }
}
