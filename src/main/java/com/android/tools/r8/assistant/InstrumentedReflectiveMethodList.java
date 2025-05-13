// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;

public class InstrumentedReflectiveMethodList {

  private DexItemFactory factory;
  private ReflectiveReferences reflectiveReferences;

  public InstrumentedReflectiveMethodList(DexItemFactory factory) {
    this.factory = factory;
    reflectiveReferences = new ReflectiveReferences(factory);
  }

  public Set<DexMethod> getInstrumentedMethodsForTesting() {
    return getInstrumentedMethodsAndTargets().keySet();
  }

  Map<DexMethod, DexMethod> getInstrumentedMethodsAndTargets() {
    return ImmutableMap.of(
        factory.classMethods.newInstance,
        getMethodReferenceWithClassParameter("onClassNewInstance"),
        factory.classMethods.getDeclaredMethod,
        getMethodReferenceWithClassMethodNameAndParameters("onClassGetDeclaredMethod"),
        factory.classMethods.forName,
        getMethodReferenceWithStringParameter("onClassForName"),
        factory.classMethods.getDeclaredField,
        getMethodReferenceWithClassAndStringParameter("onClassGetDeclaredField"),
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.createArrayType(1, factory.methodType)),
            "getDeclaredMethods"),
        getMethodReferenceWithClassParameter("onClassGetDeclaredMethods"),
        factory.classMethods.getName,
        getMethodReferenceWithClassParameter("onClassGetName"),
        factory.classMethods.getCanonicalName,
        getMethodReferenceWithClassParameter("onClassGetCanonicalName"),
        factory.classMethods.getSimpleName,
        getMethodReferenceWithClassParameter("onClassGetSimpleName"),
        factory.classMethods.getTypeName,
        getMethodReferenceWithClassParameter("onClassGetTypeName"),
        factory.classMethods.getSuperclass,
        getMethodReferenceWithClassParameter("onClassGetSuperclass"));
  }

  private DexMethod getMethodReferenceWithClassParameter(String name) {
    return getMethodReferenceWithParameterTypes(name, factory.classType);
  }

  private DexMethod getMethodReferenceWithClassAndStringParameter(String name) {
    return getMethodReferenceWithParameterTypes(name, factory.classType, factory.stringType);
  }

  private DexMethod getMethodReferenceWithStringParameter(String name) {
    return getMethodReferenceWithParameterTypes(name, factory.stringType);
  }

  private DexMethod getMethodReferenceWithParameterTypes(String name, DexType... dexTypes) {
    return factory.createMethod(
        reflectiveReferences.reflectiveOracleType,
        factory.createProto(factory.voidType, dexTypes),
        name);
  }

  private DexMethod getMethodReferenceWithClassMethodNameAndParameters(String name) {
    return factory.createMethod(
        reflectiveReferences.reflectiveOracleType,
        factory.createProto(
            factory.voidType, factory.classType, factory.stringType, factory.classArrayType),
        name);
  }
}
