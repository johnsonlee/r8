// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@KeepForApi
public class ReflectiveOperationLogger implements ReflectiveOperationReceiver {

  private final Set<String> reported = new HashSet<>();

  private void report(Stack stack, String message) {
    if (reported.add(message)) {
      System.out.println(message);
      System.out.println(stack.toStringStackTrace(3));
    }
  }

  @Override
  public void onClassNewInstance(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively created new instance of " + clazz.getName());
  }

  private String printMethod(String method, Class<?>... parameters) {
    return method + printParameters(parameters);
  }

  private String printConstructor(Class<?>... parameters) {
    return "<init>" + printParameters(parameters);
  }

  private String printParameters(Class<?>... parameters) {
    if (parameters == null) {
      return "(null)";
    }
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
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    report(
        stack,
        "Reflectively got declared method "
            + printMethod(method, parameters)
            + " on "
            + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got declared methods on " + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredField(
      Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    report(stack, "Reflectively got declared field " + fieldName + " on " + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got declared fields on " + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    report(
        stack,
        "Reflectively got declared constructor "
            + printConstructor(parameters)
            + " on "
            + clazz.getName());
  }

  @Override
  public void onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got declared constructors on " + clazz.getName());
  }

  @Override
  public void onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    report(
        stack,
        "Reflectively got method " + printMethod(method, parameters) + " on " + clazz.getName());
  }

  @Override
  public void onClassGetMethods(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got methods on " + clazz.getName());
  }

  @Override
  public void onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    report(stack, "Reflectively got field " + fieldName + " on " + clazz.getName());
  }

  @Override
  public void onClassGetFields(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got fields on " + clazz.getName());
  }

  @Override
  public void onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    report(
        stack,
        "Reflectively got constructor " + printConstructor(parameters) + " on " + clazz.getName());
  }

  @Override
  public void onClassGetConstructors(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got constructors on " + clazz.getName());
  }

  @Override
  public void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    report(
        stack, "Reflectively got name on " + clazz.getName() + "(" + lookupType.toString() + ")");
  }

  @Override
  public void onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {
    report(stack, "Reflectively called Class.forName on " + className);
  }

  @Override
  public void onClassGetComponentType(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively called Class.getComponentType on " + clazz);
  }

  @Override
  public void onClassGetPackage(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively called Class.getPackage on " + clazz);
  }

  @Override
  public void onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
    report(stack, "Reflectively called Class.isAssignableFrom on " + clazz + " with " + sup);
  }

  @Override
  public void onClassGetSuperclass(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively called Class.getSuperclass on " + clazz.getName());
  }

  @Override
  public void onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
    report(
        stack,
        "Reflectively called Class.asSubclass on " + clazz.getName() + " from " + holder.getName());
  }

  @Override
  public void onClassIsInstance(Stack stack, Class<?> holder, Object object) {
    report(
        stack, "Reflectively called Class.isInstance on " + object + " from " + holder.getName());
  }

  @Override
  public void onClassCast(Stack stack, Class<?> holder, Object object) {
    report(stack, "Reflectively called Class.cast on " + object + " from " + holder.getName());
  }

  @Override
  public void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
    report(stack, "Reflectively got class flag " + classFlag);
  }

  @Override
  public void onAtomicFieldUpdaterNewUpdater(
      Stack stack, Class<?> fieldClass, Class<?> clazz, String name) {
    report(
        stack,
        "Reflectively got AtomicReferenceFieldUpdater.newUpdater on "
            + fieldClass
            + " "
            + clazz
            + "#"
            + name);
  }

  @Override
  public void onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
    report(stack, "Reflectively got ServiceLoader.load on " + clazz + " with " + classLoader);
  }

  @Override
  public void onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {
    report(
        stack,
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
