// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx.kt

import kotlin.reflect.KProperty

class FieldsPropertyAccess {
  fun foo() {
    val x: KProperty<*> = FieldsPropertyAccessKeptClass::x
    val y: KProperty<*> = FieldsPropertyAccessKeptClass::y
    val s: KProperty<*> = FieldsPropertyAccessKeptClass::s
    println(x.call(FieldsPropertyAccessKeptClass()))
  }
}

class FieldsPropertyAccessKeptClass {
  var x: Int = 1

  var y: Long = 2

  var s: String = "3"
}

fun main() {
  FieldsPropertyAccess().foo()
}
