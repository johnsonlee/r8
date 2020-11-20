// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.b171982443

fun runSimple(cb: () -> Unit) {
  cb()
}

fun tBirdTaker() {
  runSimple { println("Hello1")}
  runSimple { println("Hello2")}
  runSimple { println("Hello3")}
}