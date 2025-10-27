// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.inline.classlevel.app

import com.android.tools.r8.kotlin.inline.classlevel.lib.A

fun main() {
  A().f()
  A().f { "Hello again, world!" }
  A().g({ "Hello, " })
}
