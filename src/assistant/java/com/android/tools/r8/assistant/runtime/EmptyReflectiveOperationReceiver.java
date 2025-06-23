// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.lang.reflect.InvocationHandler;

@KeepForApi
public class EmptyReflectiveOperationReceiver implements ReflectiveOperationReceiver {

  @Override
  public void onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {}

  @Override
  public void onClassGetComponentType(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetPackage(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {}

  @Override
  public void onClassNewInstance(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetDeclaredMethod(
      Stack stack, Class<?> clazz, String method, Class<?>... parameters) {}

  @Override
  public void onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetDeclaredField(Stack stack, Class<?> clazz, String fieldName) {}

  @Override
  public void onClassGetDeclaredFields(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {}

  @Override
  public void onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetMethod(
      Stack stack, Class<?> clazz, String method, Class<?>... parameters) {}

  @Override
  public void onClassGetMethods(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetField(Stack stack, Class<?> clazz, String fieldName) {}

  @Override
  public void onClassGetFields(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {}

  @Override
  public void onClassGetConstructors(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {}

  @Override
  public void onClassGetSuperclass(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {}

  @Override
  public void onClassIsInstance(Stack stack, Class<?> holder, Object object) {}

  @Override
  public void onClassCast(Stack stack, Class<?> holder, Object object) {}

  @Override
  public void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {}

  @Override
  public void onAtomicIntegerFieldUpdaterNewUpdater(Stack stack, Class<?> clazz, String name) {}

  @Override
  public void onAtomicLongFieldUpdaterNewUpdater(Stack stack, Class<?> clazz, String name) {}

  @Override
  public void onAtomicReferenceFieldUpdaterNewUpdater(
      Stack stack, Class<?> clazz, Class<?> fieldClass, String name) {}

  @Override
  public void onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {}

  @Override
  public void onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {}
}
