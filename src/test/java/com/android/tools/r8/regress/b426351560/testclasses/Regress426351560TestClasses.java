// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b426351560.testclasses;

public class Regress426351560TestClasses {
  interface I {
    default String defaultMethod() {
      return "Hello, world!";
    }
  }

  public static class A implements I {}
}
