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

  @Override
  public void onClassGetDeclaredMethod(
      Stack stack, Class<?> clazz, String method, Class<?>... parameters) {
    System.out.println("Reflectively got declared method " + method + " on " + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredField(Stack stack, Class<?> clazz, String fieldName) {
    System.out.println("Reflectively got declared field " + fieldName + " on " + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively got declared methods on " + clazz.getName());
  }

  @Override
  public void onClassGetMethod(Stack stack, Class<?> clazz, String method, Class<?>... parameters) {
    System.out.println("Reflectively got method " + method + " on " + clazz.getName());
  }

  @Override
  public void onClassGetField(Stack stack, Class<?> clazz, String fieldName) {
    System.out.println("Reflectively got field " + fieldName + " on " + clazz.getName());
  }

  @Override
  public void onClassGetMethods(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively got methods on " + clazz.getName());
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
