// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import java.util.Objects;

public class RetraceObjectsRequireNonNullClass {

  public static class A {

    public void run(Object o) {
      Objects.requireNonNull(o);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      (System.nanoTime() > 0 ? new A() : null).run(System.nanoTime() > 0 ? null : new A());
    }
  }
}
