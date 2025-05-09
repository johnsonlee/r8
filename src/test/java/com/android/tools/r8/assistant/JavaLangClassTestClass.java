// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import java.lang.reflect.Method;

// Top level file since the getXName methods relies on nest members being available for lookup.
public class JavaLangClassTestClass {

  public static void main(String[] args) {
    try {
      Class<?> clazz = Class.forName(Foo.class.getName());
      Class<?> superClass = clazz.getSuperclass();
      clazz.getDeclaredMethod("bar");
      clazz.getDeclaredField("a");
      clazz.getDeclaredField("b");
      Method[] declaredMethods = clazz.getDeclaredMethods();
      String s = clazz.getName();
      s += clazz.getCanonicalName();
      s += clazz.getSimpleName();
      try {
        s += clazz.getTypeName();
      } catch (NoSuchMethodError e) {
        // getTypeName is only available on 26+
        if (!e.getMessage().contains("getTypeName")) {
          throw new RuntimeException(e);
        }
      }
    } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public abstract static class Foo {
    public static int a;
    public int b;

    public static void bar() {}

    public void foo() {}

    public abstract void fooBar();
  }
}
