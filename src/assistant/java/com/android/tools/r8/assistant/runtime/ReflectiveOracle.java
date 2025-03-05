// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

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
    getInstance().onClassForName(Stack.createStack(), className);
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

  @KeepForApi
  public static class ReflectiveOperationLogger implements ReflectiveOperationReceiver {
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
    public boolean requiresStackInformation() {
      return true;
    }
  }
}
