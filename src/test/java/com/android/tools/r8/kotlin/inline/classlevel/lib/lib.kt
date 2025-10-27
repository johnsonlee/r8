// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.inline.classlevel.lib

class A {
  inline fun f() {
    println("Hello, world!")
  }

  inline fun f(supplier: () -> String) {
    println(supplier())
  }

  inline fun g(supplier1: () -> String, supplier2: () -> String = { "default" }) {
    println(supplier1() + supplier2())
  }
}
