// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

public class RetraceBackportMethodClass {

  public static class A {

    public void run(int divisor) {
      System.out.println(Math.floorDiv(42, divisor));
    }
  }

  public static class Main {

    public static void main(String[] args) {
      (System.nanoTime() > 0 ? new A() : null).run(System.nanoTime() > 0 ? 0 : 1);
    }
  }
}
