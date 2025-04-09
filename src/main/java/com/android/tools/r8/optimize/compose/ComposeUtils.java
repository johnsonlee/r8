// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.LinearFlowInstructionListIterator;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Shl;
import com.android.tools.r8.ir.code.Shr;

public class ComposeUtils {

  /**
   * Checks if the given code object executes the following instructions.
   *
   * <pre>
   * private const val changedLowBitMask = 0b001_001_001_001_001_001_001_001_001_001_0
   * private const val changedHighBitMask = changedLowBitMask shl 1
   * private const val changedMask = (changedLowBitMask or changedHighBitMask).inv()
   *
   * internal fun updateChangedFlags(flags: Int): Int {
   *     val lowBits = flags and changedLowBitMask
   *     val highBits = flags and changedHighBitMask
   *     return ((flags and changedMask) or
   *         (lowBits or (highBits shr 1)) or ((lowBits shl 1) and highBits))
   * }
   * </pre>
   */
  public static boolean isUpdateChangedFlags(IRCode code, DexItemFactory factory) {
    ProgramMethod method = code.context();
    if (!method.getAccessFlags().isStatic()
        || method.getArity() != 1
        || method.getParameter(0).isNotIdenticalTo(factory.intType)
        || method.getReturnType().isNotIdenticalTo(factory.intType)) {
      return false;
    }
    LinearFlowInstructionListIterator instructionIterator =
        new LinearFlowInstructionListIterator(code);
    Argument argument = instructionIterator.next().asArgument();
    assert argument != null;
    ConstNumber changedLowBitMask =
        instructionIterator.next().asConstNumber(0b001_001_001_001_001_001_001_001_001_001_0);
    if (changedLowBitMask == null) {
      return false;
    }
    And lowBits =
        instructionIterator.next().asAnd(argument.outValue(), changedLowBitMask.outValue());
    if (lowBits == null) {
      return false;
    }
    ConstNumber changedHighBitMask =
        instructionIterator.next().asConstNumber(0b010_010_010_010_010_010_010_010_010_010_0);
    if (changedHighBitMask == null) {
      return false;
    }
    And highBits =
        instructionIterator.next().asAnd(argument.outValue(), changedHighBitMask.outValue());
    if (highBits == null) {
      return false;
    }
    ConstNumber changedMask =
        instructionIterator.next().asConstNumber(0b1_100_100_100_100_100_100_100_100_100_100_1);
    if (changedMask == null) {
      return false;
    }
    And changedBits = instructionIterator.next().asAnd(argument.outValue(), changedMask.outValue());
    if (changedBits == null) {
      return false;
    }
    ConstNumber one = instructionIterator.next().asConstNumber(1);
    if (one == null) {
      return false;
    }
    Shr highBitsShrOne = instructionIterator.next().asShr(highBits.outValue(), one.outValue());
    if (highBitsShrOne == null) {
      return false;
    }
    Or lowBitsOrHighBitsShrOne =
        instructionIterator.next().asOr(lowBits.outValue(), highBitsShrOne.outValue());
    if (lowBitsOrHighBitsShrOne == null) {
      return false;
    }
    Or changedBitsOrLowBitsOrHighBitsShrOne =
        instructionIterator.next().asOr(changedBits.outValue(), lowBitsOrHighBitsShrOne.outValue());
    if (changedBitsOrLowBitsOrHighBitsShrOne == null) {
      return false;
    }
    ConstNumber oneAgain = instructionIterator.next().asConstNumber(1);
    if (oneAgain == null) {
      oneAgain = one;
      instructionIterator.previous();
    }
    Shl lowBitsShlOne = instructionIterator.next().asShl(lowBits.outValue(), oneAgain.outValue());
    if (lowBitsShlOne == null) {
      return false;
    }
    And lowBitsShlOneAndHighBits =
        instructionIterator.next().asAnd(lowBitsShlOne.outValue(), highBits.outValue());
    if (lowBitsShlOneAndHighBits == null) {
      return false;
    }
    Or result =
        instructionIterator
            .next()
            .asOr(
                changedBitsOrLowBitsOrHighBitsShrOne.outValue(),
                lowBitsShlOneAndHighBits.outValue());
    if (result == null) {
      return false;
    }
    Return theReturn = instructionIterator.next().asReturn();
    return theReturn != null && theReturn.returnValue() == result.outValue();
  }
}
