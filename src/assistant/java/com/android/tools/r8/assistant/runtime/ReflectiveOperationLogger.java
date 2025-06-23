// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;

@KeepForApi
public class ReflectiveOperationLogger implements ReflectiveOperationReceiver {

  @Override
  public void onClassNewInstance(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively created new instance of " + clazz.getName());
  }

  private String printMethod(String method, Class<?>... parameters) {
    return method + printParameters(parameters);
  }

  private String printConstructor(Class<?>... parameters) {
    return "<init>" + printParameters(parameters);
  }

  private String printParameters(Class<?>... parameters) {
    if (parameters.length == 0) {
      return "()";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    Class<?> first = parameters[0];
    for (Class<?> parameter : parameters) {
      if (parameter != first) {
        sb.append(", ");
      }
      sb.append(parameter.getName());
    }
    sb.append(")");
    return sb.toString();
  }

  @Override
  public void onClassGetDeclaredMethod(
      Stack stack, Class<?> clazz, String method, Class<?>... parameters) {
    System.out.println(
        "Reflectively got declared method "
            + printMethod(method, parameters)
            + " on "
            + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively got declared methods on " + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredField(Stack stack, Class<?> clazz, String fieldName) {
    System.out.println("Reflectively got declared field " + fieldName + " on " + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively got declared fields on " + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    System.out.println(
        "Reflectively got declared constructor "
            + printConstructor(parameters)
            + " on "
            + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively got declared constructors on " + clazz.getName());
  }

  @Override
  public void onClassGetMethod(Stack stack, Class<?> clazz, String method, Class<?>... parameters) {
    System.out.println(
        "Reflectively got method " + printMethod(method, parameters) + " on " + clazz.getName());
  }

  @Override
  public void onClassGetMethods(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively got methods on " + clazz.getName());
  }

  @Override
  public void onClassGetField(Stack stack, Class<?> clazz, String fieldName) {
    System.out.println("Reflectively got field " + fieldName + " on " + clazz.getName());
  }

  @Override
  public void onClassGetFields(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively got fields on " + clazz.getName());
  }

  @Override
  public void onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    System.out.println(
        "Reflectively got constructor " + printConstructor(parameters) + " on " + clazz.getName());
  }

  @Override
  public void onClassGetConstructors(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively got constructors on " + clazz.getName());
  }

  @Override
  public void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    System.out.println(
        "Reflectively got name on " + clazz.getName() + "(" + lookupType.toString() + ")");
  }

  @Override
  public void onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {
    System.out.println("Reflectively called Class.forName on " + className);
  }

  @Override
  public void onClassGetComponentType(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively called Class.getComponentType on " + clazz);
  }

  @Override
  public void onClassGetPackage(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively called Class.getPackage on " + clazz);
  }

  @Override
  public void onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
    System.out.println("Reflectively called Class.isAssignableFrom on " + clazz + " with " + sup);
  }

  @Override
  public void onClassGetSuperclass(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively called Class.getSuperclass on " + clazz.getName());
  }

  @Override
  public void onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
    System.out.println(
        "Reflectively called Class.asSubclass on " + clazz.getName() + " from " + holder.getName());
  }

  @Override
  public void onClassIsInstance(Stack stack, Class<?> holder, Object object) {
    System.out.println(
        "Reflectively called Class.isInstance on " + object + " from " + holder.getName());
  }

  @Override
  public void onClassCast(Stack stack, Class<?> holder, Object object) {
    System.out.println("Reflectively called Class.cast on " + object + " from " + holder.getName());
  }

  @Override
  public void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
    System.out.println("Reflectively got class flag " + classFlag);
  }

  @Override
  public void onAtomicIntegerFieldUpdaterNewUpdater(Stack stack, Class<?> clazz, String name) {
    System.out.println(
        "Reflectively got AtomicIntegerFieldUpdater.newUpdater on " + clazz + "#" + name);
  }

  @Override
  public void onAtomicLongFieldUpdaterNewUpdater(Stack stack, Class<?> clazz, String name) {
    System.out.println(
        "Reflectively got AtomicLongFieldUpdater.newUpdater on " + clazz + "#" + name);
  }

  @Override
  public void onAtomicReferenceFieldUpdaterNewUpdater(
      Stack stack, Class<?> clazz, Class<?> fieldClass, String name) {
    System.out.println(
        "Reflectively got AtomicReferenceFieldUpdater.newUpdater on "
            + fieldClass
            + " "
            + clazz
            + "#"
            + name);
  }

  @Override
  public void onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
    System.out.println("Reflectively got ServiceLoader.load on " + clazz + " with " + classLoader);
  }

  @Override
  public void onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {
    System.out.println(
        "Reflectively got Proxy.newProxyInstance on "
            + Arrays.toString(interfaces)
            + " with "
            + classLoader
            + " and "
            + invocationHandler);
  }

  public boolean requiresStackInformation() {
    return true;
  }
}
