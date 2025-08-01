// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding.testclasses;

public class MemberRebindingClasspathSplitTestClasses {
  public static Class<?> getA() {
    return A.class;
  }

  public static Class<?> getB() {
    return B.class;
  }

  static class A {
    public String m() {
      return "A";
    }
  }

  public static class B extends A {}
}
