// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.ThrowBlockOutlineMarker;
import com.android.tools.r8.ir.conversion.IRFinalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.utils.timing.Timing;

/** Rewriter that processes {@link ThrowBlockOutlineMarker} instructions. */
public class ThrowBlockOutlineMarkerRewriter {

  private final AppView<?> appView;
  private final DeadCodeRemover deadCodeRemover;

  ThrowBlockOutlineMarkerRewriter(AppView<?> appView) {
    this.appView = appView;
    this.deadCodeRemover = new DeadCodeRemover(appView);
  }

  public void processMethod(ProgramMethod method) {
    assert method.getDefinition().hasCode();
    assert method.getDefinition().getCode().isLirCode();
    // Build IR.
    LirCode<?> lirCode = method.getDefinition().getCode().asLirCode();
    IRCode code = lirCode.buildIR(method, appView);
    assert code.getConversionOptions().isGeneratingDex();

    // Process IR.
    processOutlineMarkers(code);

    // Convert to DEX.
    IRFinalizer<?> finalizer = code.getConversionOptions().getFinalizer(deadCodeRemover, appView);
    Code dexCode = finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(dexCode, appView);
  }

  // TODO(b/434769547): This simply removes all outline markers. We should materialize the outlines
  //  that have enough uses and rewrite the corresponding markers to call the materialized outline
  //  methods.
  private void processOutlineMarkers(IRCode code) {
    for (BasicBlock block : code.getBlocks()) {
      if (block.exit().isThrow()) {
        ThrowBlockOutlineMarker outlineMarker =
            block.entry().nextUntilInclusive(Instruction::isThrowBlockOutlineMarker);
        if (outlineMarker != null) {
          outlineMarker.removeOrReplaceByDebugLocalRead();
        }
      }
      assert block.streamInstructions().noneMatch(Instruction::isThrowBlockOutlineMarker);
    }
  }
}
