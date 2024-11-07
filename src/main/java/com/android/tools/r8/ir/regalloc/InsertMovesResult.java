// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

public class InsertMovesResult {

  private final LinearScanRegisterAllocator allocator;
  private final int numberOfParallelMoveTemporaryRegisters;

  public InsertMovesResult(
      LinearScanRegisterAllocator allocator, int numberOfParallelMoveTemporaryRegisters) {
    this.allocator = allocator;
    this.numberOfParallelMoveTemporaryRegisters = numberOfParallelMoveTemporaryRegisters;
  }

  public int getNumberOfParallelMoveTemporaryRegisters() {
    return numberOfParallelMoveTemporaryRegisters;
  }

  public void revert() {
    allocator.removeSpillAndPhiMoves();
    allocator.removeParallelMoveTemporaryRegisters();
  }
}
