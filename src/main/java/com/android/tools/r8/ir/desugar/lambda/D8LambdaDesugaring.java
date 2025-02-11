// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.utils.Timing;
import java.util.Map;

public class D8LambdaDesugaring {

  public static void rewriteEnclosingLambdaMethodAttributes(
      AppView<?> appView, Map<DexMethod, DexMethod> forcefullyMovedLambdaMethods, Timing timing) {
    if (forcefullyMovedLambdaMethods.isEmpty()) {
      return;
    }
    try (Timing t0 = timing.begin("Rewrite enclosing lambda method attributes")) {
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        if (clazz.hasEnclosingMethodAttribute()) {
          DexMethod enclosingMethod = clazz.getEnclosingMethodAttribute().getEnclosingMethod();
          DexMethod rewrittenEnclosingMethod = forcefullyMovedLambdaMethods.get(enclosingMethod);
          if (rewrittenEnclosingMethod != null) {
            clazz.setEnclosingMethodAttribute(
                new EnclosingMethodAttribute(rewrittenEnclosingMethod));
          }
        }
      }
    }
  }
}
