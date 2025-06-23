package com.android.tools.r8.keepanno.androidx.kt

import androidx.annotation.keep.UsesReflectionToConstruct
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class OnlyNoArgsConstructorClassName {
  @UsesReflectionToConstruct(
    className = "com.android.tools.r8.keepanno.androidx.kt.KeptClass",
    params = [],
  )
  fun foo(clazz: KClass<KeptClass>?) {
    clazz?.primaryConstructor?.call()
  }
}

fun main() {
  OnlyNoArgsConstructorClassName().foo(if (System.nanoTime() > 0) KeptClass::class else null)
}
