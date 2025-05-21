// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;

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
  public void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    System.out.println(
        "Reflectively got name on " + clazz.getName() + "(" + lookupType.toString() + ")");
  }

  @Override
  public void onClassForName(Stack stack, String className) {
    System.out.println("Reflectively called Class.forName on " + className);
  }

  @Override
  public void onClassGetSuperclass(Stack stack, Class<?> clazz) {
    System.out.println("Reflectively called Class.getSuperclass on " + clazz.getName());
  }

  @Override
  public void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
    System.out.println("Reflectively got class flag " + classFlag);
  }

  public boolean requiresStackInformation() {
    return true;
  }
}
