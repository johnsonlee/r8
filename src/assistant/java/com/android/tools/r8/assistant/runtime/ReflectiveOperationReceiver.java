// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public interface ReflectiveOperationReceiver {

  default boolean requiresStackInformation() {
    return false;
  }

  void onClassForName(Stack stack, String className, boolean initialize, ClassLoader classLoader);

  void onClassNewInstance(Stack stack, Class<?> clazz);

  void onClassGetDeclaredMethod(Stack stack, Class<?> clazz, String method, Class<?>... parameters);

  void onClassGetDeclaredField(Stack stack, Class<?> clazz, String fieldName);

  void onClassGetDeclaredMethods(Stack stack, Class<?> clazz);

  void onClassGetMethod(Stack stack, Class<?> clazz, String method, Class<?>... parameters);

  void onClassGetField(Stack stack, Class<?> clazz, String fieldName);

  void onClassGetMethods(Stack stack, Class<?> clazz);

  void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType);

  void onClassGetSuperclass(Stack stack, Class<?> clazz);

  void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag);

  void onClassGetComponentType(Stack stack, Class<?> clazz);

  void onClassGetPackage(Stack stack, Class<?> clazz);

  void onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup);

  @KeepForApi
  enum ClassFlag {
    ANNOTATION,
    ANONYMOUS_CLASS,
    ARRAY,
    ENUM,
    HIDDEN,
    INTERFACE,
    LOCAL_CLASS,
    MEMBER_CLASS,
    PRIMITIVE,
    RECORD,
    SEALED,
    SYNTHETIC
  }

  @KeepForApi
  enum NameLookupType {
    NAME,
    SIMPLE_NAME,
    CANONICAL_NAME,
    TYPE_NAME
  }
}
