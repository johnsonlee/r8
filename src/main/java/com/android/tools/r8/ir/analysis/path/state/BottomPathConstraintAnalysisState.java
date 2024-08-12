// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path.state;

import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;

public class BottomPathConstraintAnalysisState extends PathConstraintAnalysisState {

  private static final BottomPathConstraintAnalysisState INSTANCE =
      new BottomPathConstraintAnalysisState();

  private BottomPathConstraintAnalysisState() {}

  static BottomPathConstraintAnalysisState getInstance() {
    return INSTANCE;
  }

  @Override
  public PathConstraintAnalysisState add(ComputationTreeNode pathConstraint, boolean negate) {
    return ConcretePathConstraintAnalysisState.create(pathConstraint, negate);
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
