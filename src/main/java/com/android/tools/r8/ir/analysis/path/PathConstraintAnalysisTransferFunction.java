// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.analysis.path.state.PathConstraintAnalysisState;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;

public class PathConstraintAnalysisTransferFunction
    implements AbstractTransferFunction<BasicBlock, Instruction, PathConstraintAnalysisState> {

  @Override
  public TransferFunctionResult<PathConstraintAnalysisState> apply(
      Instruction instruction, PathConstraintAnalysisState state) {
    // TODO(b/302281503)
    throw new Unimplemented();
  }

  @Override
  public TransferFunctionResult<PathConstraintAnalysisState> applyBlock(
      BasicBlock basicBlock, PathConstraintAnalysisState state) {
    // TODO(b/302281503)
    throw new Unimplemented();
  }

  @Override
  public PathConstraintAnalysisState computeInitialState(
      BasicBlock entryBlock, PathConstraintAnalysisState bottom) {
    // TODO(b/302281503)
    throw new Unimplemented();
  }

  @Override
  public PathConstraintAnalysisState computeBlockEntryState(
      BasicBlock basicBlock,
      BasicBlock predecessor,
      PathConstraintAnalysisState predecessorExitState) {
    // TODO(b/302281503)
    throw new Unimplemented();
  }

  @Override
  public boolean shouldTransferExceptionalControlFlowFromInstruction(
      BasicBlock throwBlock, Instruction throwInstruction) {
    // TODO(b/302281503)
    throw new Unimplemented();
  }
}
