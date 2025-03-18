// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer;

import static com.android.tools.r8.utils.IntConsumerUtils.emptyIntConsumer;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfOpcodeUtils;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.google.common.collect.ImmutableList;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class CfToCfDesugaredLibraryDisableDesugarer extends DesugaredLibraryDisableDesugarer
    implements CfInstructionDesugaring {

  CfToCfDesugaredLibraryDisableDesugarer(AppView<?> appView) {
    super(appView);
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfFieldInstructionOpcodes(consumer);
    CfOpcodeUtils.acceptCfInvokeOpcodes(consumer);
    CfOpcodeUtils.acceptCfTypeInstructionOpcodes(
        opcode -> {
          // Skip primitive array allocations.
          if (opcode != Opcodes.NEWARRAY) {
            consumer.accept(opcode);
          }
        },
        emptyIntConsumer());
  }

  @Override
  public void acceptRelevantCompareToIds(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfTypeInstructionOpcodes(emptyIntConsumer(), consumer);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    CfInstruction replacement = rewriteInstruction(instruction, context);
    if (replacement == null) {
      return DesugarDescription.nothing();
    }
    return compute(replacement);
  }

  private DesugarDescription compute(CfInstruction replacement) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> ImmutableList.of(replacement))
        .build();
  }

  // TODO(b/261024278): Share this code.
  private CfInstruction rewriteInstruction(CfInstruction instruction, ProgramMethod context) {
    if (!appView.dexItemFactory().multiDexTypes.contains(context.getHolderType())) {
      return null;
    }
    if (instruction.isTypeInstruction()) {
      return rewriteTypeInstruction(instruction.asTypeInstruction());
    }
    if (instruction.isFieldInstruction()) {
      return rewriteFieldInstruction(instruction.asFieldInstruction(), context);
    }
    if (instruction.isInvoke()) {
      return rewriteInvokeInstruction(instruction.asInvoke(), context);
    }
    return null;
  }

  private CfInstruction rewriteInvokeInstruction(CfInvoke invoke, ProgramMethod context) {
    DexMethod rewrittenMethod =
        helper.rewriteMethod(invoke.getMethod(), invoke.isInterface(), context);
    return rewrittenMethod != null
        ? new CfInvoke(invoke.getOpcode(), rewrittenMethod, invoke.isInterface())
        : null;
  }

  private CfFieldInstruction rewriteFieldInstruction(
      CfFieldInstruction fieldInstruction, ProgramMethod context) {
    DexField rewrittenField = helper.rewriteField(fieldInstruction.getField(), context);
    return rewrittenField != null ? fieldInstruction.createWithField(rewrittenField) : null;
  }

  private CfInstruction rewriteTypeInstruction(CfTypeInstruction typeInstruction) {
    DexType type = typeInstruction.getType();
    DexType rewrittenType = helper.rewriteType(type);
    return type.isNotIdenticalTo(rewrittenType) ? typeInstruction.withType(rewrittenType) : null;
  }
}
