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
}
