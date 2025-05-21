// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk21.assistant;

public class JavaLangTestClass21 {

  public static void main(String[] args) {
    try {
      Class<?> clazz = Class.forName(Foo.class.getName());
      boolean ann = clazz.isAnnotation();
      boolean anc = clazz.isAnonymousClass();
      boolean arr = clazz.isArray();
      boolean enu = clazz.isEnum();
      try {
        boolean hid = clazz.isHidden();
      } catch (Throwable t) {
        System.out.println("Missing isHidden");
      }
      boolean itf = clazz.isInterface();
      boolean lcl = clazz.isLocalClass();
      boolean mem = clazz.isMemberClass();
      boolean pri = clazz.isPrimitive();
      try {
        boolean rec = clazz.isRecord();
      } catch (Throwable t) {
        System.out.println("Missing isRecord");
      }
      try {
        boolean sea = clazz.isSealed();
      } catch (Throwable t) {
        System.out.println("Missing isSealed");
      }
      boolean syn = clazz.isSynthetic();
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static class Foo {}
}
