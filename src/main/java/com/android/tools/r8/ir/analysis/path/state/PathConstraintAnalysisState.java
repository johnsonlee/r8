// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;

public abstract class PathConstraintAnalysisState
    extends AbstractState<PathConstraintAnalysisState> {

  public static BottomPathConstraintAnalysisState bottom() {
    return BottomPathConstraintAnalysisState.getInstance();
  }

  public static UnknownPathConstraintAnalysisState unknown() {
    return UnknownPathConstraintAnalysisState.getInstance();
  }

  public abstract PathConstraintAnalysisState add(
      ComputationTreeNode pathConstraint, boolean negate);

  public boolean isBottom() {
    return false;
  }

  public boolean isConcrete() {
    return false;
  }

  public boolean isUnknown() {
    return false;
  }

  @Override
  public PathConstraintAnalysisState asAbstractState() {
    return this;
  }

  public ConcretePathConstraintAnalysisState asConcreteState() {
    return null;
  }

  @Override
  public PathConstraintAnalysisState join(AppView<?> appView, PathConstraintAnalysisState other) {
    if (isBottom() || other.isUnknown()) {
      return other;
    }
    if (other.isBottom() || isUnknown()) {
      return this;
    }
    return asConcreteState().join(other.asConcreteState());
  }
}
