// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.ClassFlag;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.NameLookupType;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.Arrays;

@KeepForApi
public class ReflectiveOracle {

  private static Object instanceLock = new Object();
  private static volatile ReflectiveOperationReceiver INSTANCE;

  private static ReflectiveOperationReceiver getInstance() {
    if (INSTANCE == null) {
      // TODO(b/393249304): Support injecting alternative receiver.
      synchronized (instanceLock) {
        if (INSTANCE == null) {
          INSTANCE = getReceiver();
        }
      }
    }
    return INSTANCE;
  }

  // Might be rewritten to call new instance on a custom receiver.
  private static ReflectiveOperationReceiver getReceiver() {
    // Default, might be replaced, don't change this without changing the instrumentation
    return new ReflectiveOperationLogger();
  }

  @KeepForApi
  public static class Stack {

    private final StackTraceElement[] stackTraceElements;

    private Stack(StackTraceElement[] stackTraceElements) {
      this.stackTraceElements = stackTraceElements;
    }

    static Stack createStack() {
      assert INSTANCE != null;
      if (INSTANCE.requiresStackInformation()) {
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        return new Stack(Arrays.copyOfRange(stackTrace, 2, stackTrace.length));
      }
      return new Stack(null);
    }

    public StackTraceElement[] getStackTraceElements() {
      return stackTraceElements;
    }

    public String toStringStackTrace() {
      if (stackTraceElements == null) {
        return "Stack extraction not enabled.";
      }
      StringBuilder sb = new StringBuilder();
      for (StackTraceElement element : stackTraceElements) {
        sb.append(" at ").append(element).append("\n");
      }
      return sb.toString();
    }
  }

  public static void onClassNewInstance(Class<?> clazz) {
    getInstance().onClassNewInstance(Stack.createStack(), clazz);
  }

  public static void onClassGetDeclaredMethod(Class<?> clazz, String name, Class<?>... parameters) {
    getInstance().onClassGetDeclaredMethod(Stack.createStack(), clazz, name, parameters);
  }

  public static void onClassGetDeclaredMethods(Class<?> clazz) {
    getInstance().onClassGetDeclaredMethods(Stack.createStack(), clazz);
  }

  public static void onClassForName(String className) {
    // The last parameter is implicitly the caller's holder class class loader.
    getInstance().onClassForName(Stack.createStack(), className, true, null);
  }

  public static void onClassForName(String className, boolean initialize, ClassLoader classLoader) {
    getInstance().onClassForName(Stack.createStack(), className, initialize, classLoader);
  }

  public static void onClassGetDeclaredField(Class<?> clazz, String fieldName) {
    getInstance().onClassGetDeclaredField(Stack.createStack(), clazz, fieldName);
  }

  public static void onClassGetName(Class<?> clazz) {
    getInstance().onClassGetName(Stack.createStack(), clazz, NameLookupType.NAME);
  }

  public static void onClassGetSimpleName(Class<?> clazz) {
    getInstance().onClassGetName(Stack.createStack(), clazz, NameLookupType.SIMPLE_NAME);
  }

  public static void onClassGetCanonicalName(Class<?> clazz) {
    getInstance().onClassGetName(Stack.createStack(), clazz, NameLookupType.CANONICAL_NAME);
  }

  public static void onClassGetTypeName(Class<?> clazz) {
    getInstance().onClassGetName(Stack.createStack(), clazz, NameLookupType.TYPE_NAME);
  }

  public static void onClassGetSuperclass(Class<?> clazz) {
    getInstance().onClassGetSuperclass(Stack.createStack(), clazz);
  }

  public static void onClassGetComponentType(Class<?> clazz) {
    getInstance().onClassGetComponentType(Stack.createStack(), clazz);
  }

  public static void onClassGetPackage(Class<?> clazz) {
    getInstance().onClassGetPackage(Stack.createStack(), clazz);
  }

  public static void onClassIsAssignableFrom(Class<?> clazz, Class<?> sup) {
    getInstance().onClassIsAssignableFrom(Stack.createStack(), clazz, sup);
  }

  public static void onClassIsAnnotation(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.ANNOTATION);
  }

  public static void onClassIsAnonymousClass(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.ANONYMOUS_CLASS);
  }

  public static void onClassIsArray(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.ARRAY);
  }

  public static void onClassIsEnum(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.ENUM);
  }

  public static void onClassIsHidden(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.HIDDEN);
  }

  public static void onClassIsInterface(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.INTERFACE);
  }

  public static void onClassIsLocalClass(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.LOCAL_CLASS);
  }

  public static void onClassIsMemberClass(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.MEMBER_CLASS);
  }

  public static void onClassIsPrimitive(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.PRIMITIVE);
  }

  public static void onClassIsRecord(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.RECORD);
  }

  public static void onClassIsSealed(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.SEALED);
  }

  public static void onClassIsSynthetic(Class<?> clazz) {
    getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.SYNTHETIC);
  }

  public static void onClassGetMethods(Class<?> clazz) {
    getInstance().onClassGetMethods(Stack.createStack(), clazz);
  }

  public static void onClassGetMethod(Class<?> clazz, String name, Class<?>[] parameterTypes) {
    getInstance().onClassGetMethod(Stack.createStack(), clazz, name, parameterTypes);
  }

  public static void onClassGetField(Class<?> clazz, String name) {
    getInstance().onClassGetField(Stack.createStack(), clazz, name);
  }
}
