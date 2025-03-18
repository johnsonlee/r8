// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfOpcodeUtils;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class CfToCfDesugaredLibraryLibRewriter extends DesugaredLibraryLibRewriter
    implements CfInstructionDesugaring {

  CfToCfDesugaredLibraryLibRewriter(
      AppView<?> appView,
      Map<DexMethod, BiFunction<DexItemFactory, DexMethod, CfCode>> rewritings) {
    super(appView, rewritings);
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfInvokeOpcodes(consumer);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!isApplicableToContext(context) || !instruction.isInvoke()) {
      return DesugarDescription.nothing();
    }
    DexMethod invokedMethod = instruction.asInvoke().getMethod();
    if (!rewritings.containsKey(invokedMethod)) {
      return DesugarDescription.nothing();
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                localContext,
                methodProcessingContext,
                desugarings,
                dexItemFactory) -> {
              DexMethod newInvokeTarget =
                  getRetargetMethod(
                      invokedMethod, eventConsumer, localContext, methodProcessingContext);
              assert appView.definitionForHolder(newInvokeTarget, context) != null;
              assert !appView.definitionForHolder(newInvokeTarget, context).isInterface();
              return Collections.singletonList(
                  new CfInvoke(Opcodes.INVOKESTATIC, newInvokeTarget, false));
            })
        .build();
  }
}
