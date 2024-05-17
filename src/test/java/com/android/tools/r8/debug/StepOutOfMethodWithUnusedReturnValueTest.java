// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

public class StepOutOfMethodWithUnusedReturnValueTest {

  public static int methodWithReturnValue() {
    return System.nanoTime() > 0 ? 42 : -1;
  }

  public static void methodWithoutReturnValue() {
    System.nanoTime();
  }

  public static void main(String[] args) {
    methodWithReturnValue();
    methodWithoutReturnValue();
    methodWithReturnValue(); // another call to have a consistent continuation for two the calls.
  }
}
