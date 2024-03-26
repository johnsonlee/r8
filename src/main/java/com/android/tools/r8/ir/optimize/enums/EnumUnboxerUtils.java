// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.Value;

public class EnumUnboxerUtils {

  public static boolean isArrayUsedOnlyForHashCode(
      NewArrayFilled newArrayFilled, DexItemFactory factory) {
    Value array = newArrayFilled.outValue();
    if (array == null || !array.hasSingleUniqueUser() || array.hasPhiUsers()) {
      return false;
    }
    InvokeStatic invoke = array.singleUniqueUser().asInvokeStatic();
    if (invoke == null) {
      return false;
    }
    DexMethod invokedMethod = invoke.getInvokedMethod();
    return invokedMethod.isIdenticalTo(factory.javaUtilArraysMethods.hashCode)
        || invokedMethod.isIdenticalTo(factory.objectsMethods.hash);
  }
}
