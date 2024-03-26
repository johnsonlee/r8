// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.inlining;

public class SimpleInliningConstraintWithDepth {

  private static final SimpleInliningConstraintWithDepth NEVER =
      new SimpleInliningConstraintWithDepth(NeverSimpleInliningConstraint.getInstance(), 0);

  private final SimpleInliningConstraint constraint;
  private final int instructionDepth;

  public SimpleInliningConstraintWithDepth(
      SimpleInliningConstraint constraint, int instructionDepth) {
    this.constraint = constraint;
    this.instructionDepth = instructionDepth;
  }

  public static SimpleInliningConstraintWithDepth getAlways(int instructionDepth) {
    return AlwaysSimpleInliningConstraint.getInstance().withDepth(instructionDepth);
  }

  public static SimpleInliningConstraintWithDepth getNever() {
    return NEVER;
  }

  public SimpleInliningConstraint getConstraint() {
    return constraint;
  }

  public SimpleInliningConstraint getNopConstraint() {
    return instructionDepth == 0 ? constraint : NeverSimpleInliningConstraint.getInstance();
  }

  public int getInstructionDepth() {
    return instructionDepth;
  }

  public SimpleInliningConstraintWithDepth join(SimpleInliningConstraintWithDepth other) {
    SimpleInliningConstraint joinConstraint = constraint.join(other.constraint);
    return joinConstraint.withDepth(Math.max(instructionDepth, other.instructionDepth));
  }

  public SimpleInliningConstraintWithDepth meet(SimpleInliningConstraint other) {
    return new SimpleInliningConstraintWithDepth(constraint.meet(other), instructionDepth);
  }
}
