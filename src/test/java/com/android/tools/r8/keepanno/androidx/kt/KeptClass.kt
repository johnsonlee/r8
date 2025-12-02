// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx.kt

class KeptClass() {
  init {
    println("In KeptClass.<init>()")
  }

  constructor(i: Int) : this() {
    println("In KeptClass.<init>(Int)")
  }

  constructor(l: Long) : this() {
    println("In KeptClass.<init>(Long)")
  }

  constructor(s1: String, s2: String, s3: String) : this() {
    println("In KeptClass.<init>(String, String, String)")
  }
}
