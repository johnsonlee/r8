// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.runtime;

import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.ATOMIC_FIELD_UPDATER_NEW_UPDATER;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_AS_SUBCLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_CAST;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_FLAG;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_FOR_NAME;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_COMPONENT_TYPE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_CONSTRUCTOR;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_CONSTRUCTORS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTOR;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTORS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_FIELD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_FIELDS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_METHOD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_METHODS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_FIELD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_FIELDS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_METHOD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_METHODS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_NAME;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_PACKAGE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_SUPERCLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_IS_ASSIGNABLE_FROM;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_IS_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_NEW_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.PROXY_NEW_PROXY_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.SERVICE_LOADER_LOAD;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;

// This logs the information in JSON-like format,
// manually encoded to avoid loading gson on the mobile.
@KeepForApi
public class ReflectiveOperationJsonLogger implements ReflectiveOperationReceiver {

  private final FileWriter output;

  public ReflectiveOperationJsonLogger() throws IOException {
    String outputFileName = System.getProperty("com.android.tools.r8.reflectiveJsonLogger");
    File file;
    if (outputFileName == null) {
      String tmpDir = System.getProperty("java.io.tmpdir");
      if (tmpDir == null) {
        throw new IllegalStateException(
            "System property 'java.io.tmpdir' is null, cannot create log file.");
      }
      file = new File(tmpDir, "log.txt");
    } else {
      file = new File(outputFileName);
    }
    this.output = new FileWriter(file);
    output.write("[");
    Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown));
  }

  private void onShutdown() {
    try {
      finished();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void finished() throws IOException {
    output.write("]");
    output.close();
  }

  private String[] methodToString(
      Class<?> returnType, Class<?> holder, String method, Class<?>... parameters) {
    String[] methodStrings = new String[parameters.length + 3];
    methodStrings[0] = printClass(returnType);
    methodStrings[1] = printClass(holder);
    methodStrings[2] = method;
    for (int i = 0; i < parameters.length; i++) {
      methodStrings[i + 3] = printClass(parameters[i]);
    }
    return methodStrings;
  }

  private String[] constructorToString(Class<?> holder, Class<?>... parameters) {
    return methodToString(Void.TYPE, holder, "<init>", parameters);
  }

  private String printClass(Class<?> clazz) {
    return clazz == null ? "null" : clazz.getName();
  }

  private String printClassLoader(ClassLoader classLoader) {
    return classLoader == null ? "null" : printClass(classLoader.getClass());
  }

  private void output(ReflectiveEventType event, Stack stack, String... args) {
    try {
      output.write("{\"event\": \"");
      output.write(event.name());
      output.write("\"");
      if (stack != null) {
        output.write(", \"stack\": ");
        printArray(stack.stackTraceElementsAsString());
      }
      assert args != null;
      output.write(", \"args\": ");
      printArray(args);
      output.write("},\n");
      output.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void printArray(String... args) throws IOException {
    output.write("[");
    for (int i = 0; i < args.length; i++) {
      output.write("\"");
      output.write(args[i]);
      output.write("\"");
      if (i != args.length - 1) {
        output.write(", ");
      }
    }
    output.write("]");
  }

  @Override
  public void onClassNewInstance(Stack stack, Class<?> clazz) {
    output(CLASS_NEW_INSTANCE, stack, printClass(clazz));
  }

  @Override
  public void onClassGetDeclaredMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    output(CLASS_GET_DECLARED_METHOD, stack, methodToString(returnType, clazz, method, parameters));
  }

  @Override
  public void onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    output(CLASS_GET_DECLARED_METHODS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetDeclaredField(
      Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    output(CLASS_GET_DECLARED_FIELD, stack, printClass(fieldType), printClass(clazz), fieldName);
  }

  @Override
  public void onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
    output(CLASS_GET_DECLARED_FIELDS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    output(CLASS_GET_DECLARED_CONSTRUCTOR, stack, constructorToString(clazz, parameters));
  }

  @Override
  public void onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
    output(CLASS_GET_DECLARED_CONSTRUCTORS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    output(CLASS_GET_METHOD, stack, methodToString(returnType, clazz, method, parameters));
  }

  @Override
  public void onClassGetMethods(Stack stack, Class<?> clazz) {
    output(CLASS_GET_METHODS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    output(CLASS_GET_FIELD, stack, printClass(fieldType), printClass(clazz), fieldName);
  }

  @Override
  public void onClassGetFields(Stack stack, Class<?> clazz) {
    output(CLASS_GET_FIELDS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    output(CLASS_GET_CONSTRUCTOR, stack, constructorToString(clazz, parameters));
  }

  @Override
  public void onClassGetConstructors(Stack stack, Class<?> clazz) {
    output(CLASS_GET_CONSTRUCTORS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    output(CLASS_GET_NAME, stack, printClass(clazz), lookupType.name());
  }

  @Override
  public void onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {
    output(
        CLASS_FOR_NAME,
        stack,
        className,
        Boolean.toString(initialize),
        printClassLoader(classLoader));
  }

  @Override
  public void onClassGetComponentType(Stack stack, Class<?> clazz) {
    output(CLASS_GET_COMPONENT_TYPE, stack, printClass(clazz));
  }

  @Override
  public void onClassGetPackage(Stack stack, Class<?> clazz) {
    output(CLASS_GET_PACKAGE, stack, printClass(clazz));
  }

  @Override
  public void onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
    output(CLASS_IS_ASSIGNABLE_FROM, stack, printClass(clazz), printClass(sup));
  }

  @Override
  public void onClassGetSuperclass(Stack stack, Class<?> clazz) {
    output(CLASS_GET_SUPERCLASS, stack, printClass(clazz));
  }

  @Override
  public void onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
    output(CLASS_AS_SUBCLASS, stack, printClass(holder), printClass(clazz));
  }

  @Override
  public void onClassIsInstance(Stack stack, Class<?> holder, Object object) {
    output(
        CLASS_IS_INSTANCE,
        stack,
        printClass(holder),
        printClass(object != null ? object.getClass() : null));
  }

  @Override
  public void onClassCast(Stack stack, Class<?> holder, Object object) {
    output(CLASS_CAST, stack, printClass(holder), printClass(object.getClass()));
  }

  @Override
  public void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
    output(CLASS_FLAG, stack, printClass(clazz), classFlag.name());
  }

  @Override
  public void onAtomicFieldUpdaterNewUpdater(
      Stack stack, Class<?> fieldClass, Class<?> clazz, String name) {
    output(
        ATOMIC_FIELD_UPDATER_NEW_UPDATER, stack, printClass(fieldClass), printClass(clazz), name);
  }

  @Override
  public void onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
    output(SERVICE_LOADER_LOAD, stack, printClass(clazz), printClassLoader(classLoader));
  }

  @Override
  public void onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {
    String[] methodStrings = new String[interfaces.length + 2];
    methodStrings[0] = printClassLoader(classLoader);
    methodStrings[1] = invocationHandler.toString();
    for (int i = 0; i < interfaces.length; i++) {
      methodStrings[i + 2] = printClass(interfaces[i]);
    }
    output(PROXY_NEW_PROXY_INSTANCE, stack, methodStrings);
  }

  public boolean requiresStackInformation() {
    return true;
  }
}
