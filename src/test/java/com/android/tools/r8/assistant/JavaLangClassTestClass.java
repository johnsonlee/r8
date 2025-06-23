// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Top level file since the getXName methods relies on nest members being available for lookup.
public class JavaLangClassTestClass {

  public static void main(String[] args) {
    try {
      Class<?> clazz = Class.forName(Foo.class.getName());
      Class<?> superClass = clazz.getSuperclass();
      clazz.getDeclaredMethod("barr");
      clazz.getDeclaredField("a");
      clazz.getDeclaredField("b");
      Method[] declaredMethods = clazz.getDeclaredMethods();
      Field[] declaredFields = clazz.getDeclaredFields();
      clazz.getDeclaredConstructor();
      Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();
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
      Class<?> clazz2 = Class.forName(Foo.class.getName(), true, Foo.class.getClassLoader());
      Class<?> component = clazz2.getComponentType();
      Package pack = clazz2.getPackage();
      InputStream resStream = clazz2.getResourceAsStream("res");
      boolean ass = clazz2.isAssignableFrom(Object.class);

      Class<?> barClass = Bar.class;
      Method[] methods = barClass.getMethods();
      Field[] fields = barClass.getFields();
      Constructor<?>[] constructors = barClass.getConstructors();
      Method bar = barClass.getMethod("bar");
      Field i = barClass.getField("i");
      Constructor<?> constructor = barClass.getConstructor();

      Object o = new Bar();
      Bar cast = Bar.class.cast(o);
      boolean isInst = Bar.class.isInstance(o);
      Class<?> aClass = Bar.class.asSubclass(Foo.class);
    } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
    }
  }

  public abstract static class Foo {
    public static int a;
    public int b;

    public static void barr() {}

    public void foo() {}

    public abstract void fooBar();
  }

  public static class Bar extends Foo {
    public int i;

    public int bar() {
      return 11;
    }

    @Override
    public void fooBar() {
      System.out.println("fooBar");
    }
  }
}
