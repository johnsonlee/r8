// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx.kt

import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties

class Fields {
  fun foo(clazz: KClass<FieldsKeptClass>?) {
    println(clazz?.declaredMemberProperties?.size)
  }
}

class FieldsKeptClass {
  var x: Int = 0

  var y: Long = 0

  var s: String = ""
}

fun main() {
  Fields().foo(if (System.nanoTime() > 0) FieldsKeptClass::class else null)
}
