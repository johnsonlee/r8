// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner.conditionalsimpleinlining

fun main(args: Array<String>) {
  // Known.
  println(isEnabled("A"))
  println(isEnabled("B"))
  println(isEnabled("C"))
  println(isEnabled("D"))
  println(isEnabled("E"))
  println(isEnabled("F"))
  println(isEnabled("G"))
  println(isEnabled("H"))
  println(isEnabled("I"))
  println(isEnabled("J"))
  println(isEnabled("K"))
  println(isEnabled("L"))
  println(isEnabled("M"))
  println(isEnabled("N"))
  // Unknown.
  println(isEnabled("O"))
}

fun isEnabled(feature: String?): Boolean {
  return when (feature) {
    "A" -> true
    "B" -> false
    "C" -> true
    "D" -> true
    "E" -> false
    "F" -> true
    "G" -> false
    "H" -> false
    "I" -> false
    "J" -> false
    "K" -> true
    "L" -> true
    "M" -> false
    "N" -> true
    else -> hasProperty(feature)
  }
}

fun hasProperty(property: String?): Boolean {
  return System.getProperty(property) != null
}
