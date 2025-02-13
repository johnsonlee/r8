// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.InternalOptions;

public class EmptyVarargsUtil {
  public static void replaceWithNullIfEmptyArray(
      InvokeMethod invoke,
      int argumentIndex,
      IRCode code,
      InstructionListIterator instructionIterator,
      InternalOptions options) {
    Value argument = invoke.getArgument(argumentIndex);
    if (argument.isDefinedByInstructionSatisfying(
        i -> i.isNewArrayEmpty() && i.asNewArrayEmpty().sizeIfConst() == 0)) {
      instructionIterator.previous();
      Value replacement = instructionIterator.insertConstNullInstruction(code, options);
      invoke.replaceValue(argumentIndex, replacement);
      instructionIterator.next();
    }
  }
}
