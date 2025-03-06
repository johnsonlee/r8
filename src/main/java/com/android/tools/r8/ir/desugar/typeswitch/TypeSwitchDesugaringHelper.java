// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicReference;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.function.BiConsumer;

public class TypeSwitchDesugaringHelper {
  private static CompilationError throwEnumFieldConstantDynamic(String msg, Definition context) {
    throw new CompilationError(
        "Unexpected ConstantDynamic in TypeSwitch: " + msg, context.getOrigin());
  }

  private static boolean isTypeSwitchProto(DexProto proto) {
    return proto.getReturnType().isIntType()
        && proto.getArity() == 2
        && proto.getParameter(1).isIntType();
  }

  public static boolean isTypeSwitchCallSite(DexCallSite callSite, DexItemFactory factory) {
    return callSite.methodName.isIdenticalTo(factory.typeSwitchMethod.getName())
        && isTypeSwitchProto(callSite.methodProto)
        && methodHandleIsInvokeStaticTo(callSite.bootstrapMethod, factory.typeSwitchMethod);
  }

  public static boolean isEnumSwitchCallSite(DexCallSite callSite, DexItemFactory factory) {
    return callSite.methodName.isIdenticalTo(factory.enumSwitchMethod.getName())
        && isEnumSwitchProto(callSite.methodProto, factory.intType)
        && methodHandleIsInvokeStaticTo(callSite.bootstrapMethod, factory.enumSwitchMethod);
  }

  private static boolean methodHandleIsInvokeStaticTo(DexValue dexValue, DexMethod method) {
    if (!dexValue.isDexValueMethodHandle()) {
      return false;
    }
    return methodHandleIsInvokeStaticTo(dexValue.asDexValueMethodHandle().getValue(), method);
  }

  public static boolean methodHandleIsInvokeStaticTo(
      DexMethodHandle methodHandle, DexMethod method) {
    return methodHandle.type.isInvokeStatic() && methodHandle.asMethod().isIdenticalTo(method);
  }

  private static boolean isEnumSwitchProto(DexProto methodProto, DexType intType) {
    return methodProto.getReturnType().isIdenticalTo(intType)
        && methodProto.getArity() == 2
        && methodProto.getParameter(1).isIdenticalTo(intType);
  }

  public static void dispatchEnumField(
      BiConsumer<DexType, DexString> enumConsumer,
      ConstantDynamicReference enumCstDynamic,
      Definition context,
      DexItemFactory factory) {
    DexMethod bootstrapMethod = factory.constantDynamicBootstrapMethod;
    if (!(enumCstDynamic.getType().isIdenticalTo(factory.enumDescType)
        && enumCstDynamic.getName().isIdenticalTo(bootstrapMethod.getName())
        && enumCstDynamic.getBootstrapMethod().asMethod().isIdenticalTo(bootstrapMethod)
        && enumCstDynamic.getBootstrapMethodArguments().size() == 3
        && methodHandleIsInvokeStaticTo(
            enumCstDynamic.getBootstrapMethodArguments().get(0), factory.enumDescMethod))) {
      throw throwEnumFieldConstantDynamic("Invalid EnumDesc", context);
    }
    DexValue dexValueFieldName = enumCstDynamic.getBootstrapMethodArguments().get(2);
    if (!dexValueFieldName.isDexValueString()) {
      throw throwEnumFieldConstantDynamic("Field name " + dexValueFieldName, context);
    }
    DexString fieldName = dexValueFieldName.asDexValueString().getValue();

    DexValue dexValueClassCstDynamic = enumCstDynamic.getBootstrapMethodArguments().get(1);
    if (!dexValueClassCstDynamic.isDexValueConstDynamic()) {
      throw throwEnumFieldConstantDynamic("Enum class " + dexValueClassCstDynamic, context);
    }
    ConstantDynamicReference classCstDynamic =
        dexValueClassCstDynamic.asDexValueConstDynamic().getValue();
    if (!(classCstDynamic.getType().isIdenticalTo(factory.classDescType)
        && classCstDynamic.getName().isIdenticalTo(bootstrapMethod.getName())
        && classCstDynamic.getBootstrapMethod().asMethod().isIdenticalTo(bootstrapMethod)
        && classCstDynamic.getBootstrapMethodArguments().size() == 2
        && methodHandleIsInvokeStaticTo(
            classCstDynamic.getBootstrapMethodArguments().get(0), factory.classDescMethod))) {
      throw throwEnumFieldConstantDynamic("Class descriptor " + classCstDynamic, context);
    }
    DexValue dexValueClassName = classCstDynamic.getBootstrapMethodArguments().get(1);
    if (!dexValueClassName.isDexValueString()) {
      throw throwEnumFieldConstantDynamic("Class name " + dexValueClassName, context);
    }
    DexString className = dexValueClassName.asDexValueString().getValue();
    DexType type = factory.createType(DescriptorUtils.javaTypeToDescriptor(className.toString()));
    enumConsumer.accept(type, fieldName);
  }
}
