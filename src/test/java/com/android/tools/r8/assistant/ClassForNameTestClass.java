// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

public class ClassForNameTestClass {

  public static void main(String[] args) throws ClassNotFoundException {
    Class<?> c1 = Class.forName("com.android.tools.r8.assistant.ClassForNameTestClass");
    System.out.println(c1.getName());

    Class<?> c2 =
        Class.forName("java.lang.Object", true, ClassForNameTestClass.class.getClassLoader());
    System.out.println(c2.getName());
  }
}
