// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.R8LibraryDesugaringGraphLens;

public class LirToLirInterfaceMethodRewriter extends InterfaceMethodRewriter {

  private final CfInstructionDesugaringEventConsumer eventConsumer;

  LirToLirInterfaceMethodRewriter(
      AppView<?> appView,
      InterfaceMethodDesugaringMode desugaringMode,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    super(appView, desugaringMode);
    this.eventConsumer = eventConsumer;
  }

  public MethodLookupResult lookupMethod(
      MethodLookupResult previous,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      R8LibraryDesugaringGraphLens lens) {
    DexMethod method = previous.getReference();
    InvokeType invokeType = previous.getType();
    boolean isInterface = previous.isInterface().toBoolean();
    RetargetMethodSupplier retargetMethodSupplier =
        getRetargetMethodSupplier(method, invokeType, isInterface, context);
    // TODO(b/391572031): Add support for non-StaticRetargetMethodSupplier.
    if (retargetMethodSupplier instanceof StaticRetargetMethodSupplier) {
      StaticRetargetMethodSupplier staticRetargetMethodSupplier =
          (StaticRetargetMethodSupplier) retargetMethodSupplier;
      DexMethod retargetMethod =
          staticRetargetMethodSupplier.getRetargetMethod(eventConsumer, methodProcessingContext);
      return MethodLookupResult.builder(lens, lens.getPrevious())
          .setReference(retargetMethod)
          .setType(InvokeType.STATIC)
          .setIsInterface(false)
          .build();
    }
    return previous;
  }
}
