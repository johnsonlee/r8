package com.android.tools.r8.keepanno.androidx.kt

import androidx.annotation.keep.UsesReflectionToConstruct
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class OnlyNoArgsConstructor {
  @UsesReflectionToConstruct(classConstant = KeptClass::class, params = [])
  fun foo(clazz: KClass<KeptClass>?) {
    clazz?.primaryConstructor?.call()
  }
}

fun main() {
  OnlyNoArgsConstructor().foo(if (System.nanoTime() > 0) KeptClass::class else null)
}
