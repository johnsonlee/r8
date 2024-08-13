// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.analysis.path.state.PathConstraintAnalysisState;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameterFactory;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeBuilder;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;

public class PathConstraintAnalysisTransferFunction
    implements AbstractTransferFunction<BasicBlock, Instruction, PathConstraintAnalysisState> {

  private final ComputationTreeBuilder computationTreeBuilder;

  PathConstraintAnalysisTransferFunction(
      AbstractValueFactory abstractValueFactory,
      ProgramMethod method,
      MethodParameterFactory methodParameterFactory) {
    computationTreeBuilder =
        new ComputationTreeBuilder(abstractValueFactory, method, methodParameterFactory);
  }

  @Override
  public TransferFunctionResult<PathConstraintAnalysisState> apply(
      Instruction instruction, PathConstraintAnalysisState state) {
    // Instructions normally to not change the current path constraint.
    //
    // One exception is when information can be deduced from throwing instructions that succeed.
    // For example, if the instruction `arg.method()` succeeds then it can be inferred that the
    // subsequent instruction is only executed if `arg != null`.
    return state;
  }

  @Override
  public PathConstraintAnalysisState computeBlockEntryState(
      BasicBlock block, BasicBlock predecessor, PathConstraintAnalysisState predecessorExitState) {
    if (predecessorExitState.isUnknown()) {
      return predecessorExitState;
    }
    // We currently only amend the path constraint in presence of if-instructions.
    If theIf = predecessor.exit().asIf();
    if (theIf != null) {
      // TODO(b/302281503): Ensure the computed computation tree is cached in the builder so that
      //  we do not rebuild the tree over-and-over again during the execution of this (worklist)
      //  analysis.
      ComputationTreeNode newPathConstraint = computationTreeBuilder.buildComputationTree(theIf);
      if (!newPathConstraint.isUnknown()) {
        boolean negate = block != theIf.getTrueTarget();
        return predecessorExitState.add(newPathConstraint, negate);
      }
    }
    return predecessorExitState;
  }

  @Override
  public PathConstraintAnalysisState computeExceptionalBlockEntryState(
      BasicBlock block,
      DexType guard,
      BasicBlock throwBlock,
      Instruction throwInstruction,
      PathConstraintAnalysisState throwState) {
    // For the purpose of this analysis we don't (?) care much about the path constraints for blocks
    // that are reached from catch handlers. Therefore, we currently set the state to UNKNOWN for
    // all blocks that can be reached from a catch handler.
    return PathConstraintAnalysisState.unknown();
  }
}
