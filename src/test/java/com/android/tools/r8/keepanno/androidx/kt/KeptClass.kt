// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx.kt

class KeptClass() {
  init {
    println("<init>()")
  }

  constructor(i: Int) : this() {
    println("<init>(Int)")
  }

  constructor(l: Long) : this() {
    println("<init>(Long)")
  }

  constructor(s: String) : this() {
    println("<init>(String)")
  }
}
