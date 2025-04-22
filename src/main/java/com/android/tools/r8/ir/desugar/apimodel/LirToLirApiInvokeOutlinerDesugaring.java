// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.apimodel;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.R8LibraryDesugaringGraphLens;

public class LirToLirApiInvokeOutlinerDesugaring extends ApiInvokeOutlinerDesugaring {

  private final CfInstructionDesugaringEventConsumer eventConsumer;

  LirToLirApiInvokeOutlinerDesugaring(
      AppView<?> appView,
      AndroidApiLevelCompute apiLevelCompute,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    super(appView, apiLevelCompute);
    this.eventConsumer = eventConsumer;
  }

  public MethodLookupResult lookupMethod(
      MethodLookupResult previous,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      R8LibraryDesugaringGraphLens lens) {
    InstructionKind instructionKind;
    switch (previous.getType()) {
      case INTERFACE:
        instructionKind = InstructionKind.INVOKEINTERFACE;
        break;
      case STATIC:
        instructionKind = InstructionKind.INVOKESTATIC;
        break;
      case VIRTUAL:
        instructionKind = InstructionKind.INVOKEVIRTUAL;
        break;
      default:
        return previous;
    }
    DexMethod retargetMethod =
        getRetargetMethod(
            instructionKind, previous.getReference(), context, methodProcessingContext);
    if (retargetMethod != null) {
      return MethodLookupResult.builder(lens, lens.getPrevious())
          .setReference(retargetMethod)
          .setReboundReference(retargetMethod)
          .setIsInterface(false)
          .setType(InvokeType.STATIC)
          .build();
    }
    return previous;
  }

  public DexMethod getRetargetMethod(
      InstructionKind instructionKind,
      DexReference reference,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    RetargetMethodSupplier retargetMethodSupplier =
        getRetargetMethodSupplier(instructionKind, reference, context);
    return retargetMethodSupplier != null
        ? retargetMethodSupplier.getRetargetMethod(eventConsumer, methodProcessingContext)
        : null;
  }
}
