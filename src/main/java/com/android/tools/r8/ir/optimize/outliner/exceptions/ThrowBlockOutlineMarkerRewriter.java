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
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.ThrowBlockOutlineMarker;
import com.android.tools.r8.ir.code.UnusedArgument;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRFinalizer;
import com.android.tools.r8.ir.conversion.LensCodeArgumentRewriter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.passes.DexConstantOptimizer;
import com.android.tools.r8.ir.optimize.ConstantCanonicalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.Lir2IRConverter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collections;
import java.util.Set;

/** Rewriter that processes {@link ThrowBlockOutlineMarker} instructions. */
public class ThrowBlockOutlineMarkerRewriter {

  private final AppView<?> appView;
  private final DeadCodeRemover deadCodeRemover;

  ThrowBlockOutlineMarkerRewriter(AppView<?> appView) {
    this.appView = appView;
    this.deadCodeRemover = new DeadCodeRemover(appView);
  }

  public void processOutlineMethod(
      ProgramMethod method, LirCode<Integer> lirCode, ThrowBlockOutline outline) {
    IRCode code = buildIRForOutlineMethod(lirCode, method, outline);
    removeConstantArgumentsFromOutline(method, code, outline);
    finalizeCode(method, code);
  }

  public void processMethodWithOutlineMarkers(ProgramMethod method, LirCode<Integer> lirCode) {
    IRCode code = lirCode.buildIR(method, appView);
    processOutlineMarkers(code);
    finalizeCode(method, code);
  }

  private IRCode buildIRForOutlineMethod(
      LirCode<Integer> lirCode, ProgramMethod method, ThrowBlockOutline outline) {
    if (!outline.hasConstantArgument()) {
      return lirCode.buildIR(method, appView);
    }
    // We need to inform IR construction to insert the arguments that have been removed from the
    // proto.
    Position callerPosition = null;
    return Lir2IRConverter.translate(
        method,
        lirCode,
        LirStrategy.getDefaultStrategy().getDecodingStrategy(lirCode, new NumberGenerator()),
        appView,
        callerPosition,
        outline.getProtoChanges(),
        MethodConversionOptions.forD8(appView, method));
  }

  private void finalizeCode(ProgramMethod method, IRCode code) {
    // Convert to DEX.
    assert code.getConversionOptions().isGeneratingDex();
    IRFinalizer<?> finalizer = code.getConversionOptions().getFinalizer(deadCodeRemover, appView);
    Code dexCode = finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(dexCode, appView);
  }

  private void removeConstantArgumentsFromOutline(
      ProgramMethod method, IRCode code, ThrowBlockOutline outline) {
    if (!outline.hasConstantArgument()) {
      return;
    }

    Set<Phi> affectedPhis = Collections.emptySet();
    Set<UnusedArgument> unusedArguments = Collections.emptySet();
    new LensCodeArgumentRewriter(appView)
        .rewriteArguments(
            code, method.getReference(), outline.getProtoChanges(), affectedPhis, unusedArguments);

    // Run shorten live ranges to push materialized constants to their uses.
    ConstantCanonicalizer constantCanonicalizer = new ConstantCanonicalizer(appView, method, code);
    new DexConstantOptimizer(appView, constantCanonicalizer).run(code, Timing.empty());
  }

  private void processOutlineMarkers(IRCode code) {
    for (BasicBlock block : code.getBlocks()) {
      Throw throwInstruction = block.exit().asThrow();
      if (throwInstruction != null) {
        ThrowBlockOutlineMarker outlineMarker =
            block.entry().nextUntilInclusive(Instruction::isThrowBlockOutlineMarker);
        if (outlineMarker != null) {
          ThrowBlockOutline outline = outlineMarker.getOutline();
          outlineMarker.detachConstantOutlineArguments(outline);
          if (outline.isMaterialized()) {
            // Insert a call to the materialized outline method and load the return value.
            BasicBlockInstructionListIterator instructionIterator =
                block.listIterator(block.exit());
            InvokeStatic invoke =
                InvokeStatic.builder()
                    .setArguments(outlineMarker.inValues())
                    .setIsInterface(false)
                    .setMethod(outline.getMaterializedOutlineMethod())
                    .setPosition(throwInstruction)
                    .build();
            instructionIterator.add(invoke);
            Value returnValue = addReturnValue(code, instructionIterator);

            // Replace the throw instruction by a normal return.
            Return returnInstruction =
                Return.builder()
                    .setPositionForNonThrowingInstruction(
                        throwInstruction.getPosition(), appView.options())
                    .setReturnValue(returnValue)
                    .build();
            block.replaceLastInstruction(returnInstruction);

            // Remove all outlined instructions bottom up.
            instructionIterator = block.listIterator(invoke);
            for (Instruction instruction = instructionIterator.previous();
                instruction != outlineMarker;
                instruction = instructionIterator.previous()) {
              if (instruction.hasUnusedOutValue()) {
                instruction.removeOrReplaceByDebugLocalRead();
              }
            }
          }

          // Finally delete the outline marker.
          outlineMarker.removeOrReplaceByDebugLocalRead();
        }
      }
      assert block.streamInstructions().noneMatch(Instruction::isThrowBlockOutlineMarker);
    }

    // Run the dead code remover to ensure code that has been moved into the outline is removed
    // (e.g., constants, the allocation of the exception).
    deadCodeRemover.run(code, Timing.empty());
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
