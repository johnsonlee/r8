// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.kotlin

sealed class Greeting(val msg: String)

class Message1() : Greeting("Hello, world!")

// Unused, but referenced from the kotlin.Metadata of Greeting.
// Included for shrinking in R8 partial, but should be retained.
class Message2() : Greeting("Goodbye, world!")

fun main() {
  println(Message1().msg)
}
