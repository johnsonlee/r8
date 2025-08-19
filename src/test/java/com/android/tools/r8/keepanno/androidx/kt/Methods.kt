// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx.kt

import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions

class Methods {
  fun foo(clazz: KClass<MethodsKeptClass>?) {
    println(clazz?.declaredMemberFunctions?.filter { it.name == "m" }?.size)
  }
}

class MethodsKeptClass {
  fun m() {}

  fun m(i: Int) {}

  fun m(i: Int, l: Long) {}

  fun m(s1: String, s2: String, s3: String) {}
}

fun main() {
  Methods().foo(if (System.nanoTime() > 0) MethodsKeptClass::class else null)
}
