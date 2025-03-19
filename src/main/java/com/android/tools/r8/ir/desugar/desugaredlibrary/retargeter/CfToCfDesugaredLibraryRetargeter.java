// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfOpcodeUtils;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import java.util.Collections;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class CfToCfDesugaredLibraryRetargeter extends DesugaredLibraryRetargeter
    implements CfInstructionDesugaring {

  CfToCfDesugaredLibraryRetargeter(AppView<?> appView) {
    super(appView);
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfInvokeOpcodes(consumer);
    consumer.accept(Opcodes.GETSTATIC);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isInvoke()) {
      return computeInvokeDescription(instruction, context);
    } else if (instruction.isStaticFieldGet()) {
      return computeStaticFieldGetDescription(instruction, context);
    } else {
      return DesugarDescription.nothing();
    }
  }

  private DesugarDescription computeInvokeDescription(
      CfInstruction instruction, ProgramMethod context) {
    if (!isApplicableToContext(context)) {
      return DesugarDescription.nothing();
    }
    CfInvoke invoke = instruction.asInvoke();
    DexMethod invokedMethod = invoke.getMethod();
    InvokeType invokeType =
        invoke.getInvokeType(
            appView, context.getDefinition().getCode().getCodeLens(appView), context);
    boolean isInterface = invoke.isInterface();
    RetargetMethodSupplier retargetMethodSupplier =
        getRetargetMethodSupplier(invokedMethod, invokeType, isInterface, context);
    if (retargetMethodSupplier != null) {
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (position,
                  freshLocalProvider,
                  localStackAllocator,
                  desugaringInfo,
                  eventConsumer,
                  innerContext,
                  methodProcessingContext,
                  desugarings,
                  dexItemFactory) -> {
                DexMethod retargetMethod =
                    retargetMethodSupplier.getRetargetMethod(
                        eventConsumer, methodProcessingContext);
                assert appView.definitionForHolder(retargetMethod, innerContext) != null;
                assert !appView.definitionForHolder(retargetMethod, innerContext).isInterface();
                return Collections.singletonList(
                    new CfInvoke(Opcodes.INVOKESTATIC, retargetMethod, false));
              })
          .build();
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription computeStaticFieldGetDescription(
      CfInstruction instruction, ProgramMethod context) {
    CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
    DexField retargetField = getRetargetField(fieldInstruction.getField(), context);
    if (retargetField == null) {
      return DesugarDescription.nothing();
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context1,
                methodProcessingContext,
                desugarings,
                dexItemFactory) ->
                Collections.singletonList(fieldInstruction.createWithField(retargetField)))
        .build();
  }
}
