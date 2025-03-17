// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfOpcodeUtils;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class CfToCfDesugaredLibraryApiConverter extends DesugaredLibraryAPIConverter
    implements CfInstructionDesugaring {

  private final Set<CfInstructionDesugaring> precedingDesugarings;

  CfToCfDesugaredLibraryApiConverter(
      AppView<?> appView,
      InterfaceMethodRewriter interfaceMethodRewriter,
      Set<CfInstructionDesugaring> precedingDesugarings) {
    super(appView, interfaceMethodRewriter);
    this.precedingDesugarings = precedingDesugarings;
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfInvokeOpcodes(consumer);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvoke()) {
      return DesugarDescription.nothing();
    }
    CfInvoke invoke = instruction.asInvoke();
    InvokeType invokeType =
        invoke.getInvokeType(
            appView, context.getDefinition().getCode().getCodeLens(appView), context);
    if (!invokeNeedsDesugaring(invoke.getMethod(), invokeType, invoke.isInterface(), context)
        || isAlreadyDesugared(invoke, context)) {
      return DesugarDescription.nothing();
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                theContext,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                rewriteLibraryInvoke(
                    invoke,
                    invokeType,
                    methodProcessingContext,
                    freshLocalProvider,
                    localStackAllocator,
                    eventConsumer,
                    context))
        .build();
  }

  private boolean isAlreadyDesugared(CfInvoke invoke, ProgramMethod context) {
    return Iterables.any(
        precedingDesugarings, desugaring -> desugaring.compute(invoke, context).needsDesugaring());
  }

  private Collection<CfInstruction> rewriteLibraryInvoke(
      CfInvoke invoke,
      InvokeType invokeType,
      MethodProcessingContext methodProcessingContext,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context) {
    DexMethod retargetMethod =
        getRetargetMethod(
            invoke.getMethod(),
            invokeType,
            invoke.isInterface(),
            eventConsumer,
            context,
            methodProcessingContext);
    if (retargetMethod != null) {
      return Collections.singletonList(new CfInvoke(Opcodes.INVOKESTATIC, retargetMethod, false));
    }
    return getConversionCfProvider()
        .generateInlinedAPIConversion(
            invoke,
            invokeType,
            methodProcessingContext,
            freshLocalProvider,
            localStackAllocator,
            eventConsumer,
            context);
  }
}
