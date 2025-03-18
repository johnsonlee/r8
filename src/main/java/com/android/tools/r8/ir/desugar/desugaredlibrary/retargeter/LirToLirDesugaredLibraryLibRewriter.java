// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.R8LibraryDesugaringGraphLens;
import java.util.Map;
import java.util.function.BiFunction;

public class LirToLirDesugaredLibraryLibRewriter extends DesugaredLibraryLibRewriter {

  private final CfInstructionDesugaringEventConsumer eventConsumer;

  LirToLirDesugaredLibraryLibRewriter(
      AppView<?> appView,
      CfInstructionDesugaringEventConsumer eventConsumer,
      Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings) {
    super(appView, rewritings);
    this.eventConsumer = eventConsumer;
  }

  public MethodLookupResult lookupMethod(
      MethodLookupResult previous,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      R8LibraryDesugaringGraphLens lens) {
    if (isApplicableToContext(context)) {
      DexMethod retargetMethod =
          getRetargetMethod(
              previous.getReference(), eventConsumer, context, methodProcessingContext);
      if (retargetMethod != null) {
        return MethodLookupResult.builder(lens, lens.getPrevious())
            .setReference(retargetMethod)
            .setReboundReference(retargetMethod)
            .setIsInterface(false)
            .setType(InvokeType.STATIC)
            .build();
      }
    }
    return previous;
  }
}
