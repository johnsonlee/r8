// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.InternalOptions;

public class EmptyVarargsUtil {
  public static void replaceWithNullIfEmptyArray(
      Value value,
      IRCode code,
      InstructionListIterator instructionIterator,
      InternalOptions options,
      AffectedValues affectedValues) {
    if (value.isDefinedByInstructionSatisfying(Instruction::isNewArrayEmpty)
        && value.definition.asNewArrayEmpty().sizeIfConst() == 0) {
      instructionIterator.previous();
      value.replaceUsers(
          instructionIterator.insertConstNullInstruction(code, options), affectedValues);
      instructionIterator.next();
    }
  }
}
