// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import java.util.Arrays;

public class RegressB339064674Test {

  public static void main(String[] args) {
    for (int i : Arrays.asList(0, 1)) {
      int a1 = 1;
      if (i == 0) {
        continue;
      }
      int a2 = 2;
    }
    int a3 = 3;
  }
}
