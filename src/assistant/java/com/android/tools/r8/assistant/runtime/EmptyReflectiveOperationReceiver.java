// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public class EmptyReflectiveOperationReceiver implements ReflectiveOperationReceiver {

  @Override
  public void onClassForName(Stack stack, String className) {}

  @Override
  public void onClassNewInstance(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetDeclaredMethod(
      Stack stack, Class<?> clazz, String method, Class<?>... parameters) {}

  @Override
  public void onClassGetDeclaredField(Stack stack, Class<?> clazz, String fieldName) {}

  @Override
  public void onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {}

  @Override
  public void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {}
}
