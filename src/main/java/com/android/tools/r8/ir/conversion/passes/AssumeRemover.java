// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;

public class AssumeRemover extends CodeRewriterPass<AppInfo> {

  public AssumeRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    // We need to update the types of all values whose definitions depend on a non-null value.
    // This is needed to preserve soundness of the types after the Assume instructions have been
    // removed.
    //
    // As an example, consider a check-cast instruction on the form "z = (T) y". If y used to be
    // defined by a NonNull instruction, then the type analysis could have used this information
    // to mark z as non-null. However, cleanupNonNull() have now replaced y by a nullable value x.
    // Since z is defined as "z = (T) x", and x is nullable, it is no longer sound to have that z
    // is not nullable. This is fixed by rerunning the type analysis for the affected values.
    AffectedValues valuesThatRequireWidening = new AffectedValues();
    boolean changed = false;
    boolean needToCheckTrivialPhis = false;
    for (BasicBlock block : code.getBlocks()) {
      for (Instruction instruction = block.entry();
          instruction != null;
          instruction = instruction.getNext()) {
        Assume assumeInstruction = instruction.asAssume();
        if (assumeInstruction == null) {
          continue;
        }

        // Delete the Assume instruction and replace uses of the out-value by the in-value:
        //   y <- Assume(x)
        //   ...
        //   y.foo()
        //
        // becomes:
        //
        //   x.foo()
        Value src = assumeInstruction.src();
        Value dest = assumeInstruction.outValue();
        valuesThatRequireWidening.addAll(dest.affectedValues());

        // Replace `dest` by `src`.
        needToCheckTrivialPhis |= dest.numberOfPhiUsers() > 0;
        dest.replaceUsers(src);
        assumeInstruction.remove();
        changed = true;
      }
    }

    if (changed) {
      // Assume insertion may introduce phis, e.g.,
      //   y <- Assume(x)
      //   ...
      //   z <- phi(x, y)
      //
      // Therefore, Assume elimination may result in a trivial phi:
      //   z <- phi(x, x)
      if (needToCheckTrivialPhis) {
        code.removeAllDeadAndTrivialPhis(valuesThatRequireWidening);
      }
      valuesThatRequireWidening.widening(appView, code);
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveAssume();
  }

  @Override
  protected String getRewriterId() {
    return "AssumeRemover";
  }
}
