// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Ushr;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;

public class DivisionOptimizer extends CodeRewriterPass<AppInfo> {

  protected DivisionOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "DivisionOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return !isDebugMode(code.context()) && code.metadata().mayHaveInvokeStatic();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    IRCodeInstructionListIterator iterator = code.instructionListIterator();
    boolean hasChanged = false;
    while (iterator.hasNext()) {
      Instruction next = iterator.next();
      // `ConstXX        vb, 0xxxxx (2^k)`
      // ..
      // `InvokeStatic   { vc, va, vb } Ljava/lang/Integer;divideUnsigned(II)I`
      //
      // is converted to
      //
      // `ConstXX        vb, 0xxxxx (2^k)`
      // ..
      // `ConstXX        vd, 0xxxxx (k)`
      // `BinaryOperator { vc, va, vd } ushr`
      if (!next.isInvokeStatic()) {
        continue;
      }
      InvokeStatic invokeStatic = next.asInvokeStatic();
      Value dest = invokeStatic.outValue();
      DexMethod method = invokeStatic.getInvokedMethod();

      if (dexItemFactory.integerMembers.divideUnsigned.isIdenticalTo(method)) {
        // Int case.
        assert invokeStatic.arguments().size() == 2;
        Value dividend = invokeStatic.getFirstArgument();
        Value divisor = invokeStatic.getSecondArgument();
        if (!divisor.isConstInt()) {
          continue;
        }

        int power = extractPowerOfTwo(divisor.getConstInt());
        if (power == -1) {
          continue;
        }

        Value shiftAmount = iterator.insertConstIntInstruction(code, appView.options(), power);
        iterator.replaceCurrentInstruction(new Ushr(NumericType.INT, dest, dividend, shiftAmount));
        hasChanged = true;
      } else if (dexItemFactory.longMembers.divideUnsigned.isIdenticalTo(method)) {
        // Long case.
        assert invokeStatic.arguments().size() == 2;
        Value dividend = invokeStatic.getFirstArgument();
        Value divisor = invokeStatic.getSecondArgument();
        if (!divisor.isConstLong()) {
          continue;
        }

        int power = extractPowerOfTwo(divisor.getConstLong());
        if (power == -1) {
          continue;
        }

        Value shiftAmount = iterator.insertConstIntInstruction(code, appView.options(), power);
        iterator.replaceCurrentInstruction(new Ushr(NumericType.LONG, dest, dividend, shiftAmount));
        hasChanged = true;
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  // Returns `k` where `2^k = i` or `-1` otherwise.
  private int extractPowerOfTwo(int i) {
    if (Integer.bitCount(i) == 1 && i != 1) {
      return Integer.numberOfTrailingZeros(i);
    } else {
      return -1;
    }
  }

  // Returns `k` where `2^k = i` or `-1` otherwise.
  private int extractPowerOfTwo(long i) {
    if (Long.bitCount(i) == 1 && i != 1) {
      return Long.numberOfTrailingZeros(i);
    } else {
      return -1;
    }
  }
}
