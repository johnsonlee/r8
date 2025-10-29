// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.inline.filelevel.app

import com.android.tools.r8.kotlin.inline.filelevel.lib.f
import com.android.tools.r8.kotlin.inline.filelevel.lib.g

fun main() {
  f()
  f { "Hello again, world!" }
  g({ "Hello, " })
}
