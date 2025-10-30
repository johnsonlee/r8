// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.DebugLocalRead;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.OutlineMarker;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Return;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/** Rewriter that processes {@link OutlineMarker} instructions. */
public class OutlineMarkerRewriter {

  private final AppView<?> appView;
  private final DeadCodeRemover deadCodeRemover;
  private final DexItemFactory factory;

  OutlineMarkerRewriter(AppView<?> appView) {
    this.appView = appView;
    this.deadCodeRemover = new DeadCodeRemover(appView);
    this.factory = appView.dexItemFactory();
  }

  public void processOutlineMethod(
      ProgramMethod method, LirCode<Integer> lirCode, Outline outline) {
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
      LirCode<Integer> lirCode, ProgramMethod method, Outline outline) {
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
        appView.enableWholeProgramOptimizations()
            ? MethodConversionOptions.forLirPhase(appView)
            : MethodConversionOptions.forD8(appView, method));
  }

  private void finalizeCode(ProgramMethod method, IRCode code) {
    // Convert to DEX.
    assert appView.enableWholeProgramOptimizations()
        ? code.getConversionOptions().isGeneratingLir()
        : code.getConversionOptions().isGeneratingDex();
    IRFinalizer<?> finalizer = code.getConversionOptions().getFinalizer(deadCodeRemover, appView);
    Code dexCode = finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(dexCode, appView);
  }

  private void removeConstantArgumentsFromOutline(
      ProgramMethod method, IRCode code, Outline outline) {
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
      OutlineMarker outlineMarker = block.entry().nextUntilInclusive(Instruction::isOutlineMarker);
      while (outlineMarker != null) {
        Outline outline = outlineMarker.getOutline().getParentOrSelf();
        Instruction outlineEnd = getOutlineEnd(block, outline, outlineMarker);
        outlineMarker.detachConstantOutlineArguments(outline);
        if (outline.isMaterialized()) {
          // Insert a call to the materialized outline method and load the return value.
          BasicBlockInstructionListIterator instructionIterator = block.listIterator(outlineEnd);
          InvokeStatic.Builder invokeBuilder =
              InvokeStatic.builder()
                  .setArguments(outlineMarker.inValues())
                  .setIsInterface(false)
                  .setMethod(outline.getMaterializedOutlineMethod())
                  .setPosition(outlineEnd);
          if (outline.isStringBuilderToStringOutline()) {
            invokeBuilder.setFreshOutValue(
                code, factory.stringType.toTypeElement(appView), outlineEnd.getLocalInfo());
          }
          InvokeStatic invoke = invokeBuilder.build();
          if (outline.isStringBuilderToStringOutline()) {
            outlineEnd.replace(invoke);
            outlineEnd = invoke;
          } else {
            assert outline.isThrowOutline();
            instructionIterator.add(invoke);
            Value returnOrThrowValue = addReturnOrThrowValue(code, instructionIterator);

            // Replace the throw instruction by a normal return, but throw null in initializers.
            if (code.context().getDefinition().isInstanceInitializer()) {
              outlineEnd.replaceValue(0, returnOrThrowValue);
            } else {
              Return returnInstruction =
                  Return.builder()
                      .setPositionForNonThrowingInstruction(
                          outlineEnd.getPosition(), appView.options())
                      .setReturnValue(returnOrThrowValue)
                      .build();
              block.replaceLastInstruction(returnInstruction);
              outlineEnd = returnInstruction;
            }
          }

          // Remove all outlined instructions bottom up.
          instructionIterator = block.listIterator(invoke);
          for (Instruction outlinedInstruction = instructionIterator.previous();
              outlinedInstruction != outlineMarker;
              outlinedInstruction = instructionIterator.previous()) {
            assert !outlinedInstruction.isOutlineMarker();
            fixupOutlinedOutValue(outlinedInstruction);
            Value outValue = outlinedInstruction.outValue();
            if (outValue == null || !outValue.hasNonDebugUsers()) {
              // Remove all debug users of the out-value.
              if (outValue != null && outValue.hasDebugUsers()) {
                for (Instruction debugUser : outValue.debugUsers()) {
                  debugUser.getDebugValues().remove(outValue);
                  if (debugUser.isDebugLocalRead() && debugUser.getDebugValues().isEmpty()) {
                    debugUser.remove();
                  }
                }
                outValue.clearDebugUsers();
              }
              // We are not using `removeOrReplaceByDebugLocalRead` here due to the backwards
              // iteration.
              if (outlinedInstruction.getDebugValues().isEmpty()) {
                outlinedInstruction.remove();
              } else {
                DebugLocalRead replacement = new DebugLocalRead();
                outlinedInstruction.replace(replacement);
                Instruction previous = instructionIterator.previous();
                assert previous == replacement;
              }
            }
          }
        }

        // Finally delete the outline marker.
        outlineMarker.removeOrReplaceByDebugLocalRead();

        // Blocks cannot start with DebugLocalRead.
        while (block.entry().isDebugLocalRead()) {
          block.entry().moveDebugValues(block.entry().getNext());
          block.entry().remove();
        }

        // Continue searching for outline markers from the end of the current outline.
        outlineMarker = outlineEnd.nextUntilExclusive(Instruction::isOutlineMarker);
      }
      assert block.streamInstructions().noneMatch(Instruction::isOutlineMarker);
    }

    // TODO(b/443663978): Workaround the fact that we do not correctly patch up the debug info for
    //  LIR code in interface method desugaring. Remove when resolved.
    for (DebugLocalRead dlr : code.<DebugLocalRead>instructions(Instruction::isDebugLocalRead)) {
      if (dlr.getDebugValues().isEmpty()) {
        dlr.remove();
      }
    }

    // Run the dead code remover to ensure code that has been moved into the outline is removed
    // (e.g., constants, the allocation of the exception).
    deadCodeRemover.run(code, Timing.empty());
  }

  private Value addReturnOrThrowValue(
      IRCode code, BasicBlockInstructionListIterator instructionIterator) {
    InternalOptions options = appView.options();
    DexType returnType = code.context().getReturnType();
    // We cannot replace a throw in an instance initializer by a return, since it is a verification
    // error for instance initializers not to call a parent constructor.
    if (returnType.isReferenceType() || code.context().getDefinition().isInstanceInitializer()) {
      return instructionIterator.insertConstNullInstruction(code, options);
    } else if (returnType.isPrimitiveType()) {
      return instructionIterator.insertConstNumberInstruction(
          code, options, 0, returnType.toTypeElement(appView));
    } else {
      assert returnType.isVoidType();
      return null;
    }
  }

  // In debug mode the out-value of outlined calls to StringBuilder#append may be used outside the
  // outline. In this case we replace the out-value by the receiver of the call.
  //
  // In release mode calls to StringBuilder#append does not have an out-value, since our "returns
  // receiver" modeling replaces all uses of the out-value by the receiver.
  private void fixupOutlinedOutValue(Instruction outlinedInstruction) {
    if (outlinedInstruction.hasOutValue()
        && outlinedInstruction.outValue().hasNonDebugUsers()
        && outlinedInstruction.isInvokeVirtual()
        && factory.stringBuilderMethods.isAppendMethod(
            outlinedInstruction.asInvokeVirtual().getInvokedMethod())) {
      assert appView.options().debug;
      assert appView.options().getBottomUpOutlinerOptions().forceDebug;
      Value outlinedOutValue = outlinedInstruction.outValue();
      if (outlinedOutValue.hasDebugUsers()) {
        Collection<Instruction> debugUsers = new ArrayList<>(outlinedOutValue.debugUsers());
        outlinedOutValue.clearDebugUsers();
        for (Instruction debugUser : debugUsers) {
          debugUser.removeDebugValue(outlinedOutValue);
        }
        outlinedOutValue.clearLocalInfo();
      }
      outlinedOutValue.replaceUsers(outlinedInstruction.getFirstOperand());
    }
  }

  private Instruction getOutlineEnd(
      BasicBlock block, Outline outline, OutlineMarker outlineMarker) {
    if (outline.isThrowOutline()) {
      return block.exit();
    } else {
      // The end of a StringBuilder#toString outline is the call to StringBuilder#toString.
      return outlineMarker.nextUntilExclusive(
          i -> BottomUpOutlinerScanner.isStringBuilderToString(i, factory));
    }
  }
}
