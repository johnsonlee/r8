// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.desugar.nest;

class NestInitArgumentContextClass {
  public class Inner1 {
    private Inner1() {
      new NestInitArgumentContextClass();
    }
  }

  public class Inner2 {
    private Inner2() {}
  }

  public class Inner3 {
    private Inner3() {}
  }

  public class Inner4 {
    private Inner4() {}
  }

  private NestInitArgumentContextClass() {
    new Inner1();
    new Inner2();
    new Inner3();
    new Inner4();
  }
}
