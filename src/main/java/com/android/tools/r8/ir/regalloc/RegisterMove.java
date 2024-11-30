// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;

import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.ObjectUtils;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;

// Register moves used by the spilling register allocator. These are used both for spill and
// for phi moves and they are moves between actual registers represented by their register number.
public class RegisterMove implements Comparable<RegisterMove> {

  final TypeElement type;
  final int dst;
  final int src;
  final Instruction definition;

  public RegisterMove(int dst, int src, TypeElement type) {
    this.dst = dst;
    this.src = src;
    this.definition = null;
    this.type = type;
  }

  public RegisterMove(int dst, TypeElement type, Instruction definition) {
    assert definition.isOutConstant();
    this.dst = dst;
    this.src = NO_REGISTER;
    this.definition = definition;
    this.type = type;
  }

  public void forEachDestinationRegister(IntConsumer consumer) {
    consumer.accept(dst);
    if (isWide()) {
      consumer.accept(dst + 1);
    }
  }

  public void forEachSourceRegister(IntConsumer consumer) {
    if (src != NO_REGISTER) {
      consumer.accept(src);
      if (isWide()) {
        consumer.accept(src + 1);
      }
    }
  }

  public boolean writes(int register, boolean otherIsWide) {
    if (dst == register) {
      return true;
    }
    if (isWide() && dst + 1 == register) {
      return true;
    }
    if (otherIsWide && dst == register + 1) {
      return true;
    }
    return false;
  }

  public boolean isBlocked(
      RegisterMoveScheduler scheduler, Set<RegisterMove> moveSet, Int2IntMap valueMap) {
    if (isDestUsedAsTemporary(scheduler)) {
      return true;
    }
    for (RegisterMove move : moveSet) {
      if (isIdentical(move) || move.src == NO_REGISTER) {
        continue;
      }
      if (writes(valueMap.get(move.src), move.isWide())) {
        return true;
      }
    }
    return false;
  }

  public boolean isDestUsedAsTemporary(RegisterMoveScheduler scheduler) {
    return scheduler.activeTempRegisters.contains(dst)
        || (isWide() && scheduler.activeTempRegisters.contains(dst + 1));
  }

  // In order to unblock another move, the src register of a move can be moved to a temporary
  // register. When the move is wide, the src register may be moved to one of its own destination
  // registers.
  //
  // Example: Consider `move-wide r1, r2 <- r4, r5`. If one of the src registers r4, r5 are blocking
  // another move, we need to move r4, r5 to a temp. Since the current move-wide has not already
  // been scheduled, it must be the case that it is itself blocked, i.e., some other move is reading
  // either r1, r2, or both.
  //
  // If both r1, r2 are read by other moves, then r1, r2 cannot be used as temporary registers, and
  // there is no issue.
  //
  // If r1 is read by another move, but r2 is not, then we can use r2 as a temporary register. Thus
  // to unblock another move, we might create the temporary move `move-wide r2, r3 <- r4, r5`. A
  // prerequisite for this is that there exists a move `r3 <- _` and that `r3` is not read by any
  // moves in the move set, so that r3 can also be used as a temporary register.
  //
  // (Similarly, we could create the temporary move `move-wide r0, r1 <- r4, r5`.)
  //
  // This leads to a situation where a move is blocking itself, because it is using one of its own
  // registers as a temporary register to unblock another move.
  public boolean isDestUsedAsTemporaryForSelf(RegisterMoveScheduler scheduler) {
    assert isDestUsedAsTemporary(scheduler);
    if (!isWide()) {
      return false;
    }
    // We don't move constants to temporary registers.
    if (definition != null) {
      assert src == NO_REGISTER;
      return false;
    }
    int mappedSrc = scheduler.valueMap.get(src);
    assert mappedSrc != dst;
    if (mappedSrc == dst - 1 && scheduler.activeTempRegisters.contains(dst)) {
      return true;
    }
    if (mappedSrc == dst + 1 && scheduler.activeTempRegisters.contains(dst + 1)) {
      return true;
    }
    return false;
  }

  public boolean isIdentical(RegisterMove move) {
    return ObjectUtils.identical(this, move);
  }

  public boolean isNotIdentical(RegisterMove move) {
    return !isIdentical(move);
  }

  public boolean isWide() {
    return type.isWidePrimitive();
  }

  @Override
  public int hashCode() {
    return src + dst * 3 + type.hashCode() * 5 + Objects.hashCode(definition);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof RegisterMove)) {
      return false;
    }
    RegisterMove o = (RegisterMove) other;
    return o.src == src && o.dst == dst && type.equals(o.type) && o.definition == definition;
  }

  @Override
  public int compareTo(RegisterMove o) {
    int srcDiff = src - o.src;
    if (srcDiff != 0) {
      return srcDiff;
    }
    int dstDiff = dst - o.dst;
    if (dstDiff != 0) {
      return dstDiff;
    }
    if (type.isPrimitiveType() != o.type.isPrimitiveType()) {
      return Boolean.compare(type.isPrimitiveType(), o.type.isPrimitiveType());
    }
    if (type.isWidePrimitive() != o.type.isWidePrimitive()) {
      return Boolean.compare(type.isWidePrimitive(), o.type.isWidePrimitive());
    }
    if (type.isReferenceType() != o.type.isReferenceType()) {
      return Boolean.compare(type.isReferenceType(), o.type.isReferenceType());
    }
    if (definition == null) {
      if (o.definition != null) {
        return -1;
      }
      return 0;
    }
    if (o.definition == null) {
      return 1;
    }
    return definition.getNumber() - o.definition.getNumber();
  }

  @Override
  public String toString() {
    if (type.isSinglePrimitive()) {
      return "move " + dst + ", " + src;
    } else if (type.isWidePrimitive()) {
      return "move-wide " + dst + ", " + src;
    } else {
      assert type.isReferenceType();
      return "move-object " + dst + ", " + src;
    }
  }
}
