// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.constantdynamic;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.function.BiConsumer;

public class LibraryConstantDynamic {

  private static CompilationError throwInvalidLibraryConstantDynamic(
      String msg, Definition context) {
    throw new CompilationError("Invalid library ConstantDynamic: " + msg, context.getOrigin());
  }

  private static boolean methodHandleIsInvokeStaticTo(DexValue dexValue, DexMethod method) {
    if (!dexValue.isDexValueMethodHandle()) {
      return false;
    }
    DexMethodHandle methodHandle = dexValue.asDexValueMethodHandle().getValue();
    return methodHandle.type.isInvokeStatic() && methodHandle.asMethod().isIdenticalTo(method);
  }

  private static boolean isInvokeLibraryConstantDynamic(
      ConstantDynamicReference constantDynamic,
      DexType type,
      int argSize,
      DexMethod methodHandleMethod,
      DexItemFactory factory) {
    DexMethod bootstrapMethod = factory.constantBootstrapsMembers.invoke;
    return constantDynamic.getType().isIdenticalTo(type)
        && constantDynamic.getName().isIdenticalTo(bootstrapMethod.getName())
        && constantDynamic.getBootstrapMethod().asMethod().isIdenticalTo(bootstrapMethod)
        && constantDynamic.getBootstrapMethodArguments().size() == argSize
        && methodHandleIsInvokeStaticTo(
            constantDynamic.getBootstrapMethodArguments().get(0), methodHandleMethod);
  }

  public static boolean isPrimitiveClassConstantDynamic(
      ConstantDynamicReference constantDynamic, DexItemFactory factory) {
    DexMethod bootstrapMethod = factory.constantBootstrapsMembers.primitiveClass;
    return constantDynamic.getType().isIdenticalTo(factory.classType)
        && constantDynamic.getBootstrapMethod().asMethod().isIdenticalTo(bootstrapMethod)
        && constantDynamic.getBootstrapMethodArguments().size() == 0;
  }

  public static DexType extractPrimitiveClassConstantDynamic(
      ConstantDynamicReference constantDynamic, DexItemFactory factory, Definition context) {
    assert isPrimitiveClassConstantDynamic(constantDynamic, factory);
    String name = constantDynamic.getName().toString();
    DexType primitiveType = factory.primitiveDescriptorToType.get(name);
    if (primitiveType == null) {
      throw throwInvalidLibraryConstantDynamic("Invalid primitive type" + name, context);
    }
    return primitiveType;
  }

  public static boolean isBoxedBooleanConstantDynamic(
      ConstantDynamicReference constantDynamic, DexItemFactory factory) {
    DexMethod bootstrapMethod = factory.constantBootstrapsMembers.getStaticFinal;
    return constantDynamic.getType().isIdenticalTo(factory.boxedBooleanType)
        && constantDynamic.getBootstrapMethod().asMethod().isIdenticalTo(bootstrapMethod)
        && constantDynamic.getBootstrapMethodArguments().size() == 0;
  }

  public static boolean extractBoxedBooleanConstantDynamic(
      ConstantDynamicReference constantDynamic, DexItemFactory factory, Definition context) {
    assert isBoxedBooleanConstantDynamic(constantDynamic, factory);
    String name = constantDynamic.getName().toString();
    if (name.equals("TRUE")) {
      return true;
    }
    if (name.equals("FALSE")) {
      return false;
    }
    throw throwInvalidLibraryConstantDynamic("Invalid Boolean arg " + name, context);
  }

  public static boolean isClassDescConstantDynamic(
      ConstantDynamicReference constantDynamic, DexItemFactory factory) {
    return isInvokeLibraryConstantDynamic(
        constantDynamic, factory.classDescType, 2, factory.classDescMethod, factory);
  }

  public static DexType extractClassDescConstantDynamic(
      ConstantDynamicReference constantDynamic, DexItemFactory factory, Definition context) {
    assert isClassDescConstantDynamic(constantDynamic, factory);
    DexValue dexValueClassName = constantDynamic.getBootstrapMethodArguments().get(1);
    if (!dexValueClassName.isDexValueString()) {
      throw throwInvalidLibraryConstantDynamic("Class name " + dexValueClassName, context);
    }
    DexString className = dexValueClassName.asDexValueString().getValue();
    return factory.createType(DescriptorUtils.javaTypeToDescriptor(className.toString()));
  }

  public static boolean isEnumDescConstantDynamic(
      ConstantDynamicReference constantDynamic, DexItemFactory factory) {
    return isInvokeLibraryConstantDynamic(
        constantDynamic, factory.enumDescType, 3, factory.enumDescMethod, factory);
  }

  public static void dispatchEnumDescConstantDynamic(
      ConstantDynamicReference constantDynamic,
      DexItemFactory factory,
      Definition context,
      BiConsumer<DexType, DexString> consumer) {
    assert isEnumDescConstantDynamic(constantDynamic, factory);
    DexValue dexValueFieldName = constantDynamic.getBootstrapMethodArguments().get(2);
    if (!dexValueFieldName.isDexValueString()) {
      throw throwInvalidLibraryConstantDynamic("Enum field name " + dexValueFieldName, context);
    }
    DexValue dexValueClassCstDynamic = constantDynamic.getBootstrapMethodArguments().get(1);
    if (!dexValueClassCstDynamic.isDexValueConstDynamic()) {
      throw throwInvalidLibraryConstantDynamic("Enum class " + dexValueClassCstDynamic, context);
    }
    ConstantDynamicReference classCstDynamic =
        dexValueClassCstDynamic.asDexValueConstDynamic().getValue();
    if (!isClassDescConstantDynamic(classCstDynamic, factory)) {
      throw throwInvalidLibraryConstantDynamic("Enum class " + dexValueClassCstDynamic, context);
    }
    consumer.accept(
        extractClassDescConstantDynamic(classCstDynamic, factory, context),
        dexValueFieldName.asDexValueString().getValue());
  }
}
