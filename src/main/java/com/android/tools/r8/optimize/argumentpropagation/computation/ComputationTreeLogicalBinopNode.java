// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.ir.code.NumericType;

public abstract class ComputationTreeLogicalBinopNode extends ComputationTreeBaseNode {

  final ComputationTreeNode left;
  final ComputationTreeNode right;

  ComputationTreeLogicalBinopNode(ComputationTreeNode left, ComputationTreeNode right) {
    assert !left.isUnknown() || !right.isUnknown();
    this.left = left;
    this.right = right;
  }

  public NumericType getNumericType() {
    return NumericType.INT;
  }

  boolean internalIsEqualTo(ComputationTreeLogicalBinopNode node) {
    return left.equals(node.left) && right.equals(node.right);
  }
}
