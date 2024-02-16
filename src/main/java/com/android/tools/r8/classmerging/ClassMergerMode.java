// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging;

public enum ClassMergerMode {
  INITIAL,
  FINAL;

  public boolean isInitial() {
    return this == INITIAL;
  }

  public boolean isFinal() {
    return this == FINAL;
  }
}
