// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static java.util.Collections.emptyList;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.ThrowBlockOutlineMarker;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.base.Equivalence.Wrapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ThrowBlockOutlinerScanner {

  private static final IRMetadata metadata = IRMetadata.unknown();

  private final AppView<?> appView;
  private final DexItemFactory factory;

  private final Map<Wrapper<LirCode<?>>, ThrowBlockOutline> outlines = new ConcurrentHashMap<>();

  ThrowBlockOutlinerScanner(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  public void run(IRCode code) {
    new ThrowBlockOutlinerScannerForCode(code).run();
  }

  public Collection<ThrowBlockOutline> getOutlines() {
    return outlines.values();
  }

  private class ThrowBlockOutlinerScannerForCode {

    private final IRCode code;

    ThrowBlockOutlinerScannerForCode(IRCode code) {
      this.code = code;
    }

    private void run() {
      assert !code.metadata().mayHaveThrowBlockOutlineMarker();
      for (BasicBlock block : getThrowBlocks()) {
        processThrowBlock(block);
      }
      if (code.metadata().mayHaveThrowBlockOutlineMarker()) {
        assert code.getConversionOptions().isGeneratingDex();
        code.mutateConversionOptions(MutableMethodConversionOptions::setIsGeneratingLir);
      } else {
        assert code.streamInstructions().noneMatch(Instruction::isThrowBlockOutlineMarker);
      }
    }

    private List<BasicBlock> getThrowBlocks() {
      boolean seenReturn = false;
      List<BasicBlock> throwBlocks = new ArrayList<>();
      for (BasicBlock block : code.getBlocks()) {
        if (block.exit().isReturn()) {
          seenReturn = true;
        } else if (block.exit().isThrow()) {
          throwBlocks.add(block);
        }
      }
      // Never outline from methods that always throw.
      return seenReturn ? throwBlocks : emptyList();
    }

    private void processThrowBlock(BasicBlock block) {
      // Recursively build up the outline method. On successful outline creation, the resulting
      // LirCode is passed to the continuation function.
      processThrowInstruction(
          block,
          block.exit().asThrow(),
          outlineBuilder -> {
            // On successful outline creation, store the outline for later processing.
            LirCode<?> lirCode = outlineBuilder.build(appView, code.context());
            Wrapper<LirCode<?>> lirCodeWrapper =
                ThrowBlockOutlinerLirCodeEquivalence.get().wrap(lirCode);
            ThrowBlockOutline outline =
                outlines.computeIfAbsent(lirCodeWrapper, w -> new ThrowBlockOutline(w.get()));
            outline.addUser(code.reference());

            // Insert a synthetic marker instruction that references the outline so that we know
            // where to materialize the outline call.
            Instruction insertionPoint = outlineBuilder.getFirstOutlinedInstruction();
            assert insertionPoint.getBlock() == block;
            ThrowBlockOutlineMarker marker =
                ThrowBlockOutlineMarker.builder()
                    .setOutline(outline)
                    .setPosition(Position.none())
                    .build();
            block.listIterator(insertionPoint).add(marker);
          });
    }

    private void processThrowInstruction(
        BasicBlock throwBlock, Throw throwInstruction, Consumer<OutlineBuilder> continuation) {
      Value exceptionValue = throwInstruction.exception();
      if (!exceptionValue.isDefinedByInstructionSatisfying(
          i -> i.isNewInstance() && i.getBlock() == throwBlock)) {
        // Exception is not created in the throw block.
        return;
      }
      assert throwInstruction.hasPrev();
      processExceptionConstructorCall(
          throwBlock,
          throwInstruction.getPrev(),
          outlineBuilder -> {
            Value outlinedExceptionValue = outlineBuilder.getOutlinedValue(exceptionValue);
            outlineBuilder.add(
                Throw.builder()
                    .setExceptionValue(outlinedExceptionValue)
                    .setPosition(Position.syntheticNone())
                    .build());
            continuation.accept(outlineBuilder);
          });
    }

    private void processExceptionConstructorCall(
        BasicBlock throwBlock, Instruction instruction, Consumer<OutlineBuilder> continuation) {
      InvokeDirect invoke = instruction.asInvokeConstructor(factory);
      if (invoke == null) {
        return;
      }
      DexMethod constructor = invoke.getInvokedMethod();
      if (!constructor.getParameters().isEmpty()) {
        // TODO(b/434769547): Handle constructors with arguments.
        return;
      }
      processNewExceptionInstruction(
          throwBlock,
          invoke.getPrev(),
          outlineBuilder -> {
            Value outlinedExceptionValue = outlineBuilder.getOutlinedValue(invoke.getReceiver());
            outlineBuilder.add(
                InvokeDirect.builder()
                    .setMethod(constructor)
                    .setPosition(Position.syntheticNone())
                    .setSingleArgument(outlinedExceptionValue)
                    .build());
            continuation.accept(outlineBuilder);
          });
    }

    private void processNewExceptionInstruction(
        BasicBlock throwBlock, Instruction instruction, Consumer<OutlineBuilder> continuation) {
      NewInstance newInstance = instruction.asNewInstance();
      if (newInstance == null) {
        return;
      }
      // Check that this is the thrown exception.
      if (newInstance.outValue() != throwBlock.exit().asThrow().exception()) {
        return;
      }
      OutlineBuilder outlineBuilder = new OutlineBuilder(newInstance);
      NewInstance outlinedNewInstance =
          NewInstance.builder()
              .setFreshOutValue(
                  outlineBuilder.valueNumberGenerator,
                  newInstance.getType().toNonNullClassTypeElement(appView))
              .setType(newInstance.getType())
              .setPosition(Position.syntheticNone())
              .build();
      outlineBuilder.add(outlinedNewInstance);
      outlineBuilder.map(newInstance.outValue(), outlinedNewInstance.outValue());
      continuation.accept(outlineBuilder);
    }
  }

  private static class OutlineBuilder {

    private final Instruction firstOutlinedInstruction;

    private final BasicBlock outlinedBlock = new BasicBlock(metadata);

    // Map from non-outlined values to their corresponding outlined values.
    private final Map<Value, Value> outlinedValues = new IdentityHashMap<>();

    private final NumberGenerator blockNumberGenerator = new NumberGenerator();
    private final NumberGenerator valueNumberGenerator = new NumberGenerator();

    OutlineBuilder(Instruction firstOutlinedInstruction) {
      this.firstOutlinedInstruction = firstOutlinedInstruction;
      outlinedBlock.setNumber(blockNumberGenerator.next());
    }

    void add(Instruction instruction) {
      outlinedBlock.add(instruction, metadata);
    }

    void map(Value value, Value outlinedValue) {
      assert !outlinedValues.containsKey(value);
      outlinedValues.put(value, outlinedValue);
    }

    Instruction getFirstOutlinedInstruction() {
      return firstOutlinedInstruction;
    }

    Value getOutlinedValue(Value value) {
      Value outlinedValue = outlinedValues.get(value);
      assert outlinedValue != null;
      return outlinedValue;
    }

    LirCode<?> build(AppView<?> appView, ProgramMethod context) {
      outlinedBlock.setFilled();
      IRCode outlineCode =
          new IRCode(
              appView.options(),
              null,
              SyntheticPosition.syntheticNone(),
              ListUtils.newLinkedList(outlinedBlock),
              valueNumberGenerator,
              blockNumberGenerator,
              metadata,
              MethodConversionOptions.forLirPhase(appView)) {

            @Override
            public DexMethod reference() {
              return context.getReference();
            }

            @Override
            public boolean isD8R8Synthesized() {
              return true;
            }
          };
      LirCode<?> lirCode =
          new IRToLirFinalizer(appView)
              .finalizeCode(outlineCode, BytecodeMetadataProvider.empty(), Timing.empty());
      return lirCode;
    }
  }
}
