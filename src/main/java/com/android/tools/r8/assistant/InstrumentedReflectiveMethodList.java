// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
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
    ImmutableMap.Builder<DexMethod, DexMethod> builder = ImmutableMap.builder();

    // java.lang.Class methods

    builder.put(
        factory.classMethods.newInstance,
        getMethodReferenceWithClassParameter("onClassNewInstance"));
    builder.put(
        factory.classMethods.getDeclaredMethod,
        getMethodReferenceWithClassMethodNameAndParameters("onClassGetDeclaredMethod"));
    builder.put(
        factory.classMethods.forName, getMethodReferenceWithStringParameter("onClassForName"));
    builder.put(
        factory.classMethods.getDeclaredField,
        getMethodReferenceWithClassAndStringParameter("onClassGetDeclaredField"));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.createArrayType(1, factory.methodType)),
            "getDeclaredMethods"),
        getMethodReferenceWithClassParameter("onClassGetDeclaredMethods"));
    builder.put(
        factory.classMethods.getName, getMethodReferenceWithClassParameter("onClassGetName"));
    builder.put(
        factory.classMethods.getCanonicalName,
        getMethodReferenceWithClassParameter("onClassGetCanonicalName"));
    builder.put(
        factory.classMethods.getSimpleName,
        getMethodReferenceWithClassParameter("onClassGetSimpleName"));
    builder.put(
        factory.classMethods.getTypeName,
        getMethodReferenceWithClassParameter("onClassGetTypeName"));
    builder.put(
        factory.classMethods.getSuperclass,
        getMethodReferenceWithClassParameter("onClassGetSuperclass"));
    builder.put(
        factory.classMethods.forName3,
        getMethodReferenceWithParameterTypes(
            "onClassForName", factory.stringType, factory.booleanType, factory.classLoaderType));
    builder.put(
        factory.createMethod(
            factory.classType, factory.createProto(factory.classType), "getComponentType"),
        getMethodReferenceWithClassParameter("onClassGetComponentType"));
    builder.put(
        factory.classMethods.getPackage, getMethodReferenceWithClassParameter("onClassGetPackage"));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.booleanType, factory.classType),
            "isAssignableFrom"),
        getMethodReferenceWithParameterTypes(
            "onClassIsAssignableFrom", factory.classType, factory.classType));
    DexProto toBoolean = factory.createProto(factory.booleanType);
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isAnnotation"),
        getMethodReferenceWithClassParameter("onClassIsAnnotation"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isAnonymousClass"),
        getMethodReferenceWithClassParameter("onClassIsAnonymousClass"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isArray"),
        getMethodReferenceWithClassParameter("onClassIsArray"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isEnum"),
        getMethodReferenceWithClassParameter("onClassIsEnum"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isHidden"),
        getMethodReferenceWithClassParameter("onClassIsHidden"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isInterface"),
        getMethodReferenceWithClassParameter("onClassIsInterface"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isLocalClass"),
        getMethodReferenceWithClassParameter("onClassIsLocalClass"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isMemberClass"),
        getMethodReferenceWithClassParameter("onClassIsMemberClass"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isPrimitive"),
        getMethodReferenceWithClassParameter("onClassIsPrimitive"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isRecord"),
        getMethodReferenceWithClassParameter("onClassIsRecord"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isSealed"),
        getMethodReferenceWithClassParameter("onClassIsSealed"));
    builder.put(
        factory.createMethod(factory.classType, toBoolean, "isSynthetic"),
        getMethodReferenceWithClassParameter("onClassIsSynthetic"));
    builder.put(
        factory.classMethods.getMethod,
        getMethodReferenceWithParameterTypes(
            "onClassGetMethod", factory.classType, factory.stringType, factory.classArrayType));
    builder.put(
        factory.classMethods.getField,
        getMethodReferenceWithParameterTypes(
            "onClassGetField", factory.classType, factory.stringType));
    builder.put(
        factory.createMethod(
            factory.classType,
            factory.createProto(factory.createArrayType(1, factory.methodType)),
            "getMethods"),
        getMethodReferenceWithClassParameter("onClassGetMethods"));

    builder.put(
        factory.atomicFieldUpdaterMethods.intUpdater,
        getMethodReferenceWithClassAndStringParameter("onAtomicIntegerFieldUpdaterNewUpdater"));
    builder.put(
        factory.atomicFieldUpdaterMethods.longUpdater,
        getMethodReferenceWithClassAndStringParameter("onAtomicLongFieldUpdaterNewUpdater"));
    builder.put(
        factory.atomicFieldUpdaterMethods.referenceUpdater,
        getMethodReferenceWithParameterTypes(
            "onAtomicReferenceFieldUpdaterNewUpdater",
            factory.classType,
            factory.classType,
            factory.stringType));

    builder.put(
        factory.serviceLoaderMethods.load,
        getMethodReferenceWithClassParameter("onServiceLoaderLoad"));
    builder.put(
        factory.serviceLoaderMethods.loadWithClassLoader,
        getMethodReferenceWithParameterTypes(
            "onServiceLoaderLoadWithClassLoader", factory.classType, factory.classLoaderType));
    builder.put(
        factory.serviceLoaderMethods.loadInstalled,
        getMethodReferenceWithClassParameter("onServiceLoaderLoadInstalled"));

    builder.put(
        factory.proxyMethods.newProxyInstance,
        getMethodReferenceWithParameterTypes(
            "onProxyNewProxyInstance",
            factory.classLoaderType,
            factory.classArrayType,
            factory.invocationHandlerType));

    return builder.build();
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
