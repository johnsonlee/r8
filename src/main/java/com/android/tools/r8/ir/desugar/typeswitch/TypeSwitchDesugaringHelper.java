// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;

public class TypeSwitchDesugaringHelper {

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

  private static boolean methodHandleIsInvokeStaticTo(
      DexMethodHandle methodHandle, DexMethod method) {
    return methodHandle.type.isInvokeStatic() && methodHandle.asMethod().isIdenticalTo(method);
  }

  private static boolean isEnumSwitchProto(DexProto methodProto, DexType intType) {
    return methodProto.getReturnType().isIdenticalTo(intType)
        && methodProto.getArity() == 2
        && methodProto.getParameter(1).isIdenticalTo(intType);
  }
}
