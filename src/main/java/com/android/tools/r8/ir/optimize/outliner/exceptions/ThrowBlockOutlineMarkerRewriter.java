// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.ThrowBlockOutlineMarker;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRFinalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.utils.InternalOptions;
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
      Throw throwInstruction = block.exit().asThrow();
      if (throwInstruction != null) {
        ThrowBlockOutlineMarker outlineMarker =
            block.entry().nextUntilInclusive(Instruction::isThrowBlockOutlineMarker);
        if (outlineMarker != null) {
          ThrowBlockOutline outline = outlineMarker.getOutline();
          if (outline.isMaterialized()) {
            // Insert a call to the materialized outline method and load the return value.
            BasicBlockInstructionListIterator instructionIterator =
                block.listIterator(outlineMarker);
            instructionIterator.add(
                InvokeStatic.builder()
                    .setIsInterface(false)
                    .setMethod(outline.getMaterializedOutlineMethod())
                    .setPosition(throwInstruction)
                    .build());
            Value returnValue = addReturnValue(code, instructionIterator);

            // Replace the throw instruction by a normal return.
            Return returnInstruction =
                Return.builder().setPosition(Position.none()).setReturnValue(returnValue).build();
            block.replaceLastInstruction(returnInstruction);

            // Remove all outlined instructions bottom up.
            instructionIterator = block.listIterator(returnInstruction);
            while (instructionIterator.previous() != outlineMarker) {
              instructionIterator.removeOrReplaceByDebugLocalRead();
            }
          }

          // Finally delete the outline marker.
          outlineMarker.removeOrReplaceByDebugLocalRead();
        }
      }
      assert block.streamInstructions().noneMatch(Instruction::isThrowBlockOutlineMarker);
    }
  }

  private Value addReturnValue(IRCode code, BasicBlockInstructionListIterator instructionIterator) {
    InternalOptions options = appView.options();
    DexType returnType = code.context().getReturnType();
    if (returnType.isVoidType()) {
      return null;
    } else if (returnType.isPrimitiveType()) {
      return instructionIterator.insertConstNumberInstruction(
          code, options, 0, returnType.toTypeElement(appView));
    } else {
      assert returnType.isReferenceType();
      return instructionIterator.insertConstNullInstruction(code, options);
    }
  }
}
