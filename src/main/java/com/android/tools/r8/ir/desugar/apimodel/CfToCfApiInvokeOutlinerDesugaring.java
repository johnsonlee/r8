// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.apimodel;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfOpcodeUtils;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import java.util.Collections;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class CfToCfApiInvokeOutlinerDesugaring extends ApiInvokeOutlinerDesugaring
    implements CfInstructionDesugaring {

  CfToCfApiInvokeOutlinerDesugaring(AppView<?> appView, AndroidApiLevelCompute apiLevelCompute) {
    super(appView, apiLevelCompute);
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfFieldInstructionOpcodes(consumer);
    CfOpcodeUtils.acceptCfInvokeOpcodes(
        opcode -> {
          if (opcode != Opcodes.INVOKESPECIAL) {
            consumer.accept(opcode);
          }
        });
    consumer.accept(Opcodes.CHECKCAST);
    consumer.accept(Opcodes.INSTANCEOF);
  }

  @Override
  public void acceptRelevantCompareToIds(IntConsumer consumer) {
    consumer.accept(CfCompareHelper.CONST_CLASS_COMPARE_ID);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    InstructionKind instructionKind;
    DexReference reference;
    if (instruction.hasAsmOpcode()) {
      switch (instruction.getAsmOpcode()) {
        case Opcodes.CHECKCAST:
          instructionKind = InstructionKind.CHECKCAST;
          reference = instruction.asCheckCast().getType();
          break;
        case Opcodes.GETFIELD:
          instructionKind = InstructionKind.IGET;
          reference = instruction.asFieldInstruction().getField();
          break;
        case Opcodes.GETSTATIC:
          instructionKind = InstructionKind.SGET;
          reference = instruction.asFieldInstruction().getField();
          break;
        case Opcodes.INSTANCEOF:
          instructionKind = InstructionKind.INSTANCEOF;
          reference = instruction.asInstanceOf().getType();
          break;
        case Opcodes.INVOKEINTERFACE:
          instructionKind = InstructionKind.INVOKEINTERFACE;
          reference = instruction.asInvoke().getMethod();
          break;
        case Opcodes.INVOKESTATIC:
          instructionKind = InstructionKind.INVOKESTATIC;
          reference = instruction.asInvoke().getMethod();
          break;
        case Opcodes.INVOKEVIRTUAL:
          instructionKind = InstructionKind.INVOKEVIRTUAL;
          reference = instruction.asInvoke().getMethod();
          break;
        case Opcodes.PUTFIELD:
          instructionKind = InstructionKind.IPUT;
          reference = instruction.asFieldInstruction().getField();
          break;
        case Opcodes.PUTSTATIC:
          instructionKind = InstructionKind.SPUT;
          reference = instruction.asFieldInstruction().getField();
          break;
        default:
          return DesugarDescription.nothing();
      }
    } else if (instruction.isConstClass()) {
      instructionKind = InstructionKind.CONSTCLASS;
      reference = instruction.asConstClass().getType();
    } else {
      return DesugarDescription.nothing();
    }
    RetargetMethodSupplier retargetMethodSupplier =
        getRetargetMethodSupplier(instructionKind, reference, context);
    if (retargetMethodSupplier != null) {
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (position,
                  freshLocalProvider,
                  localStackAllocator,
                  desugaringInfo,
                  eventConsumer,
                  context1,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                DexMethod retargetMethod =
                    retargetMethodSupplier.getRetargetMethod(
                        eventConsumer, methodProcessingContext);
                return Collections.singletonList(
                    new CfInvoke(Opcodes.INVOKESTATIC, retargetMethod, false));
              })
          .build();
    }
    return DesugarDescription.nothing();
  }
}
