// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.named_arguments_app

import com.android.tools.r8.kotlin.metadata.named_arguments_lib.Lib

fun main() {
  println(Lib().computeMethod(first = "1", second = "2", third = "3"))
  println(Lib().computeMethod(third = "1", second = "2", first = "3"))
}
