// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.OriginalFieldWitnessInstruction;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;

public class OriginalFieldWitnessRemover extends CodeRewriterPass<AppInfo> {

  public OriginalFieldWitnessRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "OriginalFieldWitnessRemover";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveOriginalFieldWitness();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      if (current.isOriginalFieldWitness()) {
        OriginalFieldWitnessInstruction instruction = current.asOriginalFieldWitness();
        instruction.outValue().replaceUsers(instruction.src());
        iterator.removeOrReplaceByDebugLocalRead();
        hasChanged = true;
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }
}
