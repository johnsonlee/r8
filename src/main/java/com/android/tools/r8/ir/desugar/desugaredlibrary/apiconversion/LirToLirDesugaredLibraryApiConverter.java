// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.R8LibraryDesugaringGraphLens;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryAPIConverterEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;

public class LirToLirDesugaredLibraryApiConverter extends DesugaredLibraryAPIConverter {

  private final DesugaredLibraryAPIConverterEventConsumer eventConsumer;

  LirToLirDesugaredLibraryApiConverter(
      AppView<?> appView,
      DesugaredLibraryAPIConverterEventConsumer eventConsumer,
      InterfaceMethodRewriter interfaceMethodRewriter) {
    super(appView, interfaceMethodRewriter);
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
    if (!invokeNeedsDesugaring(method, invokeType, isInterface, context)) {
      return previous;
    }
    DexMethod retargetMethod =
        getRetargetMethod(
            method, invokeType, isInterface, eventConsumer, context, methodProcessingContext);
    if (retargetMethod != null) {
      return MethodLookupResult.builder(lens, lens.getPrevious())
          .setReference(retargetMethod)
          .setReboundReference(retargetMethod)
          .setIsInterface(false)
          .setType(InvokeType.STATIC)
          .build();
    } else {
      return MethodLookupResult.builder(lens, lens.getPrevious())
          .setReference(previous.getReference())
          .setReboundReference(previous.getReboundReference())
          .setIsInterface(isInterface)
          .setNeedsDesugaredLibraryApiConversion()
          .setType(previous.getType())
          .build();
    }
  }
}
