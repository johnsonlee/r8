// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;

public class UnsplitArgumentsResult {

  private final LinearScanRegisterAllocator allocator;
  private final Reference2IntMap<LiveIntervals> originalRegisterAssignment;

  public UnsplitArgumentsResult(
      LinearScanRegisterAllocator allocator,
      Reference2IntMap<LiveIntervals> originalRegisterAssignment) {
    assert originalRegisterAssignment.defaultReturnValue() == NO_REGISTER;
    this.allocator = allocator;
    this.originalRegisterAssignment = originalRegisterAssignment;
  }

  public boolean isFullyReverted() {
    return originalRegisterAssignment.isEmpty();
  }

  // Returns true if any changes were made.
  public boolean revertPartial() {
    boolean changed = false;
    for (LiveIntervals argumentLiveIntervals : allocator.getArgumentLiveIntervals()) {
      for (LiveIntervals child : argumentLiveIntervals.getSplitChildren()) {
        changed |= revertPartial(child);
      }
    }
    return changed;
  }

  private boolean revertPartial(LiveIntervals intervals) {
    int originalRegister = originalRegisterAssignment.getInt(intervals);
    if (originalRegister == NO_REGISTER) {
      // This live intervals was not affected by the unsplit arguments optimization.
      return false;
    }
    int conservativeRealRegisterEnd =
        allocator.realRegisterNumberFromAllocated(intervals.getRegisterEnd());
    if (conservativeRealRegisterEnd <= intervals.getRegister()) {
      return false;
    }
    // Apply revert.
    intervals.clearRegisterAssignment();
    intervals.setRegister(originalRegister);
    originalRegisterAssignment.removeInt(intervals);
    return true;
  }
}
