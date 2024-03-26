// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.ResourceConstNumber;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.DescriptorUtils;

public class ConstResourceNumberRewriter extends CodeRewriterPass<AppInfo> {
  public ConstResourceNumberRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "ConstResourceNumberRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return appView.options().isOptimizedResourceShrinking()
        && code.context().getDefinition().isClassInitializer()
        && isRClass(code.context().getHolder());
  }

  private boolean isRClass(DexProgramClass holder) {
    return DescriptorUtils.isRClassDescriptor(holder.getType().toDescriptorString());
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      if (current.isConstNumber()) {
        ConstNumber constNumber = current.asConstNumber();
        // The resource const numbers should always have a single value here
        Value currentValue = current.outValue();
        if (currentValue.hasSingleUniqueUser() && !currentValue.hasPhiUsers()) {
          Instruction singleUser = currentValue.singleUniqueUser();
          if (singleUser.isStaticPut()
              || singleUser.isNewArrayFilled()
              || (singleUser.isArrayPut() && singleUser.asArrayPut().value() == currentValue)) {
            iterator.replaceCurrentInstruction(
                new ResourceConstNumber(constNumber.dest(), constNumber.getIntValue()));
            hasChanged = true;
          }
        }
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  @Override
  protected boolean verifyConsistentCode(IRCode code, boolean ssa, String preposition) {
    // Skip verification since this runs prior to the removal of invalid code.
    return true;
  }
}
