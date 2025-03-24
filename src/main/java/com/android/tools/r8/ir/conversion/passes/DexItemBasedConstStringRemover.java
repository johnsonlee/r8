// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;

public class DexItemBasedConstStringRemover extends CodeRewriterPass<AppInfo> {

  public DexItemBasedConstStringRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "DexItemBasedConstStringRemover";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveDexItemBasedConstString();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      DexItemBasedConstString instruction = iterator.next().asDexItemBasedConstString();
      if (instruction != null) {
        DexString replacement =
            instruction.getNameComputationInfo().computeNameFor(instruction.getItem(), appView());
        iterator.replaceCurrentInstructionWithConstString(appView, code, replacement, null);
        hasChanged = true;
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }
}
