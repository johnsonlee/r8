// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

public enum PartialCompilationTestParameters {
  NONE,
  INCLUDE_ALL,
  EXCLUDE_ALL,
  RANDOM;

  public boolean isNone() {
    return this == NONE;
  }

  public boolean isSome() {
    return !isNone();
  }

  public boolean isIncludeAll() {
    return this == INCLUDE_ALL;
  }

  public boolean isExcludeAll() {
    return this == EXCLUDE_ALL;
  }

  public boolean isRandom() {
    return this == RANDOM;
  }
}
