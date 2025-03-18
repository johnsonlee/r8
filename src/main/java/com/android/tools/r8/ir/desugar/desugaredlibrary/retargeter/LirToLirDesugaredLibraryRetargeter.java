// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.R8LibraryDesugaringGraphLens;

public class LirToLirDesugaredLibraryRetargeter extends DesugaredLibraryRetargeter {

  private final CfInstructionDesugaringEventConsumer eventConsumer;

  LirToLirDesugaredLibraryRetargeter(
      AppView<?> appView, CfInstructionDesugaringEventConsumer eventConsumer) {
    super(appView);
    this.eventConsumer = eventConsumer;
  }

  public FieldLookupResult lookupField(
      FieldLookupResult previous, ProgramMethod context, R8LibraryDesugaringGraphLens lens) {
    DexField retargetField = getRetargetField(previous.getReference(), context);
    if (retargetField != null) {
      assert !previous.hasReadCastType();
      assert !previous.hasWriteCastType();
      return FieldLookupResult.builder(lens)
          .setReference(retargetField)
          .setReboundReference(retargetField)
          .build();
    }
    return previous;
  }

  public MethodLookupResult lookupMethod(
      MethodLookupResult previous,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      R8LibraryDesugaringGraphLens lens) {
    if (isApplicableToContext(context)) {
      RetargetMethodSupplier retargetMethodSupplier =
          getRetargetMethodSupplier(
              previous.getReference(),
              previous.getType(),
              previous.isInterface().toBoolean(),
              context);
      if (retargetMethodSupplier != null) {
        DexMethod retargetMethod =
            retargetMethodSupplier.getRetargetMethod(eventConsumer, methodProcessingContext);
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
