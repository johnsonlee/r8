/*
 * Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
 * for details. All rights reserved. Use of this source code is governed by a
 * BSD-style license that can be found in the LICENSE file.
 */
package com.android.tools.r8.kotlin.access.b369418242

import com.android.tools.r8.kotlin.access.b369418242.pkg.B

fun main(args: Array<String>) {
  // Kotlinc generates bytecode for `child.doSomething((A) B())`
  val child = B()
  child.doSomething(B())
}
