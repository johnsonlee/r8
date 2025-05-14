// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.IdentifierNameStringUtils;

public class ConstantValueUtils {

  /**
   * If the given value is a constant class, then returns the corresponding {@link DexType}.
   * Otherwise returns null. This should only be used for tracing.
   */
  public static DexType getDexTypeRepresentedByValueForTracing(
      Value value, AppView<? extends AppInfoWithClassHierarchy> appView) {
    Value alias =
        value.getAliasedValue(IgnoreDebugLocalWriteAliasedValueConfiguration.getInstance());
    if (alias.isPhi()) {
      return null;
    }

    if (alias.definition.isConstClass()) {
      return alias.definition.asConstClass().getType();
    }

    if (alias.definition.isInvokeStatic()) {
      InvokeStatic invoke = alias.definition.asInvokeStatic();
      if (appView
          .dexItemFactory()
          .classMethods
          .isReflectiveClassLookup(invoke.getInvokedMethod())) {
        return getDexTypeFromClassForName(invoke, appView);
      }
    }

    return null;
  }

  public static DexClass getClassFromClassForName(
      InvokeStatic invoke, AppView<? extends AppInfoWithClassHierarchy> appView) {
    DexType type = getDexTypeFromClassForName(invoke, appView);
    if (type != null && type.isClassType()) {
      return appView.definitionFor(type);
    }
    return null;
  }

  public static DexType getDexTypeFromClassForName(
      InvokeStatic invoke, AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert appView.dexItemFactory().classMethods.isReflectiveClassLookup(invoke.getInvokedMethod());
    if (invoke.arguments().size() == 1 || invoke.arguments().size() == 3) {
      Value argument = invoke.arguments().get(0);
      if (argument.isConstString()) {
        ConstString constStringInstruction = argument.getConstInstruction().asConstString();
        return IdentifierNameStringUtils.inferClassTypeFromNameString(
            appView, constStringInstruction.getValue());
      }
      if (argument.isDexItemBasedConstString()) {
        DexItemBasedConstString constStringInstruction =
            argument.getConstInstruction().asDexItemBasedConstString();
        DexReference item = constStringInstruction.getItem();
        if (item.isDexType()) {
          return item.asDexType();
        }
      }
    }
    return null;
  }
}
