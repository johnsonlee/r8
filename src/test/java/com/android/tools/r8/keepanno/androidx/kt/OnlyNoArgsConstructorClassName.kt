// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx.kt

import androidx.annotation.keep.UsesReflectionToConstruct
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class OnlyNoArgsConstructorClassName {
  @UsesReflectionToConstruct(
    className = "com.android.tools.r8.keepanno.androidx.kt.KeptClass",
    parameterTypes = [],
  )
  fun foo(clazz: KClass<KeptClass>?) {
    println(clazz?.primaryConstructor)
    clazz?.primaryConstructor?.call()
    val noArgsConstructor = clazz?.constructors?.find { it.parameters.isEmpty() }
    println(noArgsConstructor)
    noArgsConstructor?.call()
  }
}

fun main() {
  OnlyNoArgsConstructorClassName().foo(if (System.nanoTime() > 0) KeptClass::class else null)
}
