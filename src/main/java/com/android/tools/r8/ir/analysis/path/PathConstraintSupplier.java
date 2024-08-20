// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.SuccessfulDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.path.state.PathConstraintAnalysisState;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameterFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class PathConstraintSupplier {

  private final AppView<AppInfoWithLiveness> appView;
  private final IRCode code;
  private final MethodParameterFactory methodParameterFactory;

  private SuccessfulDataflowAnalysisResult<BasicBlock, PathConstraintAnalysisState>
      pathConstraintAnalysisResult;

  public PathConstraintSupplier(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      MethodParameterFactory methodParameterFactory) {
    this.appView = appView;
    this.code = code;
    this.methodParameterFactory = methodParameterFactory;
  }

  public PathConstraintAnalysisState getPathConstraint(BasicBlock block) {
    if (pathConstraintAnalysisResult == null) {
      PathConstraintAnalysis analysis =
          new PathConstraintAnalysis(appView, code, methodParameterFactory);
      pathConstraintAnalysisResult = analysis.run(code.entryBlock()).asSuccessfulAnalysisResult();
      assert pathConstraintAnalysisResult != null;
    }
    return pathConstraintAnalysisResult.getBlockExitState(block);
  }
}
