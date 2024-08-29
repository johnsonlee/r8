// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.SuccessfulDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.path.state.ConcretePathConstraintAnalysisState;
import com.android.tools.r8.ir.analysis.path.state.PathConstraintAnalysisState;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldValueFactory;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameterFactory;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class PathConstraintSupplier {

  private final AppView<AppInfoWithLiveness> appView;
  private final IRCode code;
  private final FieldValueFactory fieldValueFactory;
  private final MethodParameterFactory methodParameterFactory;

  private SuccessfulDataflowAnalysisResult<BasicBlock, PathConstraintAnalysisState>
      pathConstraintAnalysisResult;

  public PathConstraintSupplier(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      FieldValueFactory fieldValueFactory,
      MethodParameterFactory methodParameterFactory) {
    this.appView = appView;
    this.code = code;
    this.fieldValueFactory = fieldValueFactory;
    this.methodParameterFactory = methodParameterFactory;
  }

  public ComputationTreeNode getDifferentiatingPathConstraint(
      BasicBlock block, BasicBlock otherBlock) {
    ConcretePathConstraintAnalysisState state = getPathConstraint(block).asConcreteState();
    if (state == null) {
      return AbstractValue.unknown();
    }
    ConcretePathConstraintAnalysisState otherState =
        getPathConstraint(otherBlock).asConcreteState();
    if (otherState == null) {
      return AbstractValue.unknown();
    }
    return state.getDifferentiatingPathConstraint(otherState);
  }

  public PathConstraintAnalysisState getPathConstraint(BasicBlock block) {
    if (pathConstraintAnalysisResult == null) {
      PathConstraintAnalysis analysis =
          new PathConstraintAnalysis(appView, code, fieldValueFactory, methodParameterFactory);
      pathConstraintAnalysisResult = analysis.run(code.entryBlock()).asSuccessfulAnalysisResult();
      assert pathConstraintAnalysisResult != null;
    }
    return pathConstraintAnalysisResult.getBlockExitState(block);
  }
}
