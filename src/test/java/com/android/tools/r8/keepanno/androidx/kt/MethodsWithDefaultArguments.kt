// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx.kt

import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions

class MethodsWithDefaultArguments {
  fun foo() {
    val f: KFunction<*>? =
      MethodsWithDefaultArgumentsKeptClass::class.memberFunctions.firstOrNull { it.name == "m" }
    println(f?.parameters?.size)
    val receiverParameter = f?.parameters?.get(0)!!
    val xParameter = f.parameters[1]
    val yParameter = f.parameters[2]
    println(f.callBy(mapOf(receiverParameter to MethodsWithDefaultArgumentsKeptClass())))
    println(
      f.callBy(mapOf(receiverParameter to MethodsWithDefaultArgumentsKeptClass(), xParameter to 2))
    )
    println(
      f.callBy(mapOf(receiverParameter to MethodsWithDefaultArgumentsKeptClass(), yParameter to 4))
    )
    println(
      f.callBy(
        mapOf(
          receiverParameter to MethodsWithDefaultArgumentsKeptClass(),
          xParameter to 3,
          yParameter to 3,
        )
      )
    )
  }
}

class MethodsWithDefaultArgumentsKeptClass {
  var z = 1

  fun m(x: Int = 1, y: Int = 2): Int = x + y + z
}

fun main() {
  MethodsWithDefaultArguments().foo()
}
