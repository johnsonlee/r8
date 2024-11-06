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

  public boolean isBlocked(Set<RegisterMove> moveSet, Int2IntMap valueMap) {
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

  public boolean isIdentical(RegisterMove move) {
    return ObjectUtils.identical(this, move);
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
}
