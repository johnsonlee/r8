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
      System.out.println(clazz.getSuperclass().getSimpleName());
      System.out.println(clazz.getDeclaredMethod("barr").getName());
      System.out.println(clazz.getDeclaredField("a").getName());
      System.out.println(clazz.getDeclaredField("b").getName());
      Method[] declaredMethods = clazz.getDeclaredMethods();
      Field[] declaredFields = clazz.getDeclaredFields();
      System.out.println(clazz.getDeclaredConstructor().getName());
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
      System.out.println(s);
      Class<?> clazz2 = Class.forName(Foo.class.getName(), true, Foo.class.getClassLoader());
      Class<?> componentType = clazz2.getComponentType();
      System.out.println(clazz2.getPackage().getName());
      InputStream resStream = clazz2.getResourceAsStream("res");
      boolean ass = clazz2.isAssignableFrom(Object.class);

      Class<?> barClass = Bar.class;
      Method[] methods = barClass.getMethods();
      Field[] fields = barClass.getFields();
      Constructor<?>[] constructors = barClass.getConstructors();
      System.out.println(barClass.getMethod("bar"));
      System.out.println(barClass.getField("i"));
      System.out.println(barClass.getConstructor());

      Object o = new Bar();
      Bar cast = Bar.class.cast(o);
      System.out.println(Bar.class.isInstance(o));
      System.out.println(Bar.class.asSubclass(Foo.class));
      Bar newBar = null;
      try {
        newBar = Bar.class.newInstance();
        System.out.println(newBar.bar());
      } catch (InstantiationException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      System.out.println("END");
    } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
      System.out.println("EXCEPTION " + e.getMessage());
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
