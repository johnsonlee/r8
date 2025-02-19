// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.syntheticmethodforannotations.kt

import kotlin.reflect.full.declaredMemberProperties

annotation class Test

class Data {
  companion object {
    @Test var All: Set<String> = emptySet()

    @property:Test var All2: Set<String> = emptySet()
  }
}

fun main() {
  println("start")
  Data.Companion::class.declaredMemberProperties.forEach {
    println("${it.name} has @Test: ${it.annotations.filterIsInstance<Test>().any()}")
  }
  println("end")
}
