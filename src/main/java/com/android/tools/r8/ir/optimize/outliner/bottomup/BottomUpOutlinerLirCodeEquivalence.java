// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup;

import com.android.tools.r8.lightir.LirCode;
import com.google.common.base.Equivalence;
import java.util.Arrays;

/** An equivalence for the simple type of LirCode produced by the {@link BottomUpOutliner}. */
public class BottomUpOutlinerLirCodeEquivalence extends Equivalence<LirCode<?>> {

  private static final BottomUpOutlinerLirCodeEquivalence INSTANCE =
      new BottomUpOutlinerLirCodeEquivalence();

  public static BottomUpOutlinerLirCodeEquivalence get() {
    return INSTANCE;
  }

  @Override
  protected boolean doEquivalent(LirCode<?> lirCode, LirCode<?> other) {
    assert verifyLirCode(lirCode);
    assert verifyLirCode(other);
    return lirCode.getArgumentCount() == other.getArgumentCount()
        && lirCode.getInstructionCount() == other.getInstructionCount()
        && Arrays.equals(lirCode.getInstructionBytes(), other.getInstructionBytes())
        && Arrays.equals(lirCode.getConstantPool(), other.getConstantPool());
  }

  @Override
  protected int doHash(LirCode<?> lirCode) {
    assert verifyLirCode(lirCode);
    return 31 * (31 + Arrays.hashCode(lirCode.getConstantPool()))
        + Arrays.hashCode(lirCode.getInstructionBytes());
  }

  private boolean verifyLirCode(LirCode<?> lirCode) {
    assert !lirCode.hasDebugLocalInfoTable();
    assert !lirCode.hasPositionTable();
    assert !lirCode.hasTryCatchTable();
    return true;
  }
}
