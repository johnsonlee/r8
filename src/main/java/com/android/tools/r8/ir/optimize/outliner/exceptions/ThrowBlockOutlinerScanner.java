// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;
import static java.util.Collections.emptyList;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeUtils;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.type.TypeUtils;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ThrowBlockOutlinerScanner {

  private static final IRMetadata metadata = IRMetadata.unknown();

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final AbstractValueFactory valueFactory = new AbstractValueFactory();

  private final Map<Wrapper<LirCode<?>>, ThrowBlockOutline> outlines = new ConcurrentHashMap<>();

  ThrowBlockOutlinerScanner(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  public void run(IRCode code) {
    new ThrowBlockOutlinerScannerForCode(code).run();
  }

  public AbstractValueFactory getAbstractValueFactory() {
    // If/when extending this to R8, use the R8 AbstractValueFactory from AppView.
    assert !appView.enableWholeProgramOptimizations();
    return valueFactory;
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
            LirCode<?> lirCode = outlineBuilder.buildLirCode(appView, code.context());
            Wrapper<LirCode<?>> lirCodeWrapper =
                ThrowBlockOutlinerLirCodeEquivalence.get().wrap(lirCode);
            DexProto proto = outlineBuilder.getProto(appView);
            ThrowBlockOutline outline =
                outlines.computeIfAbsent(
                    lirCodeWrapper, w -> new ThrowBlockOutline(w.get(), proto));
            assert proto.isIdenticalTo(outline.getProto());
            List<Value> arguments = outlineBuilder.buildArguments();
            outline.addUser(code.reference(), arguments, getAbstractValueFactory());

            // Insert a synthetic marker instruction that references the outline so that we know
            // where to materialize the outline call.
            Instruction insertionPoint = outlineBuilder.getFirstOutlinedInstruction();
            assert insertionPoint.getBlock() == block;
            ThrowBlockOutlineMarker marker =
                ThrowBlockOutlineMarker.builder()
                    .setArguments(arguments)
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
      // We always expect the constructor call corresponding to the thrown exception to be last.
      processExceptionConstructorCall(
          throwBlock,
          throwInstruction.getPrev(),
          outlineBuilder -> {
            Value outlinedExceptionValue = outlineBuilder.getOutlinedValue(exceptionValue);
            if (outlinedExceptionValue == null) {
              // Fail as we were unable to outline the corresponding new-instance instruction.
              return;
            }
            outlineBuilder.add(
                Throw.builder()
                    .setExceptionValue(outlinedExceptionValue)
                    .setPosition(Position.syntheticNone())
                    .build());
            continuation.accept(outlineBuilder);
          });
    }

    private void processInstruction(
        BasicBlock throwBlock, Instruction instruction, Consumer<OutlineBuilder> continuation) {
      switch (instruction.opcode()) {
        case CONST_NUMBER:
        case CONST_STRING:
          processConstInstruction(throwBlock, instruction.asConstInstruction(), continuation);
          return;
        case INVOKE_DIRECT:
          if (instruction.isInvokeConstructor(factory)) {
            processStringBuilderConstructorCall(
                throwBlock, instruction.asInvokeDirect(), continuation);
            return;
          }
          break;
        case INVOKE_STATIC:
          processStringFormatOrValueOf(throwBlock, instruction.asInvokeStatic(), continuation);
          return;
        case INVOKE_VIRTUAL:
          processStringBuilderAppendOrToString(
              throwBlock, instruction.asInvokeVirtual(), continuation);
          return;
        case NEW_INSTANCE:
          processNewInstanceInstruction(throwBlock, instruction.asNewInstance(), continuation);
          return;
        default:
          break;
      }
      // Unhandled instruction. Start the outline at the successor instruction.
      startOutline(instruction.getNext(), continuation);
    }

    private void processConstInstruction(
        BasicBlock throwBlock,
        ConstInstruction instruction,
        Consumer<OutlineBuilder> continuation) {
      processPredecessorInstructionOrStartOutline(throwBlock, instruction, continuation);
    }

    private void processPredecessorInstructionOrFail(
        BasicBlock throwBlock, Instruction instruction, Consumer<OutlineBuilder> continuation) {
      if (instruction.hasPrev()) {
        processInstruction(throwBlock, instruction.getPrev(), continuation);
      } else {
        // Intentionally empty. Not calling the continuation corresponds to dropping the outline.
      }
    }

    private void processPredecessorInstructionOrStartOutline(
        BasicBlock throwBlock, Instruction instruction, Consumer<OutlineBuilder> continuation) {
      if (instruction.hasPrev()) {
        processInstruction(throwBlock, instruction.getPrev(), continuation);
      } else {
        startOutline(instruction, continuation);
      }
    }

    private void processNewInstanceInstruction(
        BasicBlock throwBlock, NewInstance newInstance, Consumer<OutlineBuilder> continuation) {
      if (newInstance.outValue() != throwBlock.exit().asThrow().exception()
          && newInstance.getType().isNotIdenticalTo(appView.dexItemFactory().stringBuilderType)) {
        // Unhandled instruction.
        startOutline(newInstance.getNext(), continuation);
        return;
      }
      processPredecessorInstructionOrStartOutline(
          throwBlock,
          newInstance,
          outlineBuilder -> {
            outlineNewInstanceInstruction(newInstance, outlineBuilder);
            continuation.accept(outlineBuilder);
          });
    }

    private void outlineNewInstanceInstruction(
        NewInstance newInstance, OutlineBuilder outlineBuilder) {
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
    }

    private void processExceptionConstructorCall(
        BasicBlock throwBlock, Instruction instruction, Consumer<OutlineBuilder> continuation) {
      InvokeDirect invoke = instruction.asInvokeConstructor(factory);
      if (invoke == null) {
        // Not a constructor call.
        return;
      }
      Throw throwInstruction = throwBlock.exit().asThrow();
      Value exceptionValue = throwInstruction.exception();
      assert !exceptionValue.hasDebugUsers();
      assert !exceptionValue.hasPhiUsers();
      if (invoke.getReceiver() != exceptionValue) {
        // Not the constructor call corresponding to the thrown exception.
        return;
      }
      // This instruction is guaranteed to have a predecessor since the handling of the throw
      // instruction checks if the new-instance instruction is in the throw block.
      assert instruction.hasPrev();
      processPredecessorInstructionOrFail(
          throwBlock,
          invoke,
          outlineBuilder -> {
            if (outlineBuilder.getOutlinedValue(exceptionValue) == null) {
              // We were unable to outline the corresponding new-instance instruction. Check if we
              // can insert it here, right before the constructor call.
              NewInstance newInstance = exceptionValue.getDefinition().asNewInstance();
              if (exceptionValue.uniqueUsers().size() == 2
                  && !newInstance.instructionMayHaveSideEffects(appView, code.context())) {
                // The exception value is only used by the constructor call and the throw.
                // By construction, it cannot have debug users nor phi users.
                outlineNewInstanceInstruction(newInstance, outlineBuilder);
              } else {
                // Fail as we were unable to outline the corresponding new-instance instruction.
                return;
              }
            }
            outlineBuilder.add(
                InvokeDirect.builder()
                    .setArguments(
                        ListUtils.map(
                            invoke.arguments(), outlineBuilder::getOutlinedValueOrCreateArgument))
                    .setMethod(invoke.getInvokedMethod())
                    .setPosition(Position.syntheticNone())
                    .build());
            continuation.accept(outlineBuilder);
          });
    }

    private void processStringBuilderConstructorCall(
        BasicBlock throwBlock, InvokeDirect invoke, Consumer<OutlineBuilder> continuation) {
      if (!factory.stringBuilderMethods.isConstructorMethod(invoke.getInvokedMethod())) {
        // Unhandled instruction.
        startOutline(invoke.getNext(), continuation);
        return;
      }
      processPredecessorInstructionOrFail(
          throwBlock,
          invoke,
          outlineBuilder -> {
            if (outlineBuilder.getOutlinedValue(invoke.getReceiver()) == null) {
              // Fail as we were unable to outline the corresponding new-instance instruction.
              return;
            }
            outlineBuilder.add(
                InvokeDirect.builder()
                    .setArguments(
                        ListUtils.map(
                            invoke.arguments(), outlineBuilder::getOutlinedValueOrCreateArgument))
                    .setMethod(invoke.getInvokedMethod())
                    .setPosition(Position.syntheticNone())
                    .build());
            continuation.accept(outlineBuilder);
          });
    }

    private void processStringFormatOrValueOf(
        BasicBlock throwBlock, InvokeStatic invoke, Consumer<OutlineBuilder> continuation) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!invokedMethod.isIdenticalTo(factory.stringMembers.format)
          && !invokedMethod.isIdenticalTo(factory.stringMembers.valueOf)) {
        // Unhandled instruction.
        startOutline(invoke.getNext(), continuation);
        return;
      }
      processPredecessorInstructionOrStartOutline(
          throwBlock,
          invoke,
          outlineBuilder -> {
            InvokeStatic.Builder outlinedInvokeBuilder =
                InvokeStatic.builder()
                    .setArguments(
                        ListUtils.map(
                            invoke.arguments(), outlineBuilder::getOutlinedValueOrCreateArgument))
                    .setMethod(invoke.getInvokedMethod())
                    .setPosition(Position.syntheticNone());
            if (invoke.hasOutValue()) {
              outlinedInvokeBuilder.setFreshOutValue(
                  outlineBuilder.valueNumberGenerator, invoke.getOutType());
            }
            InvokeStatic outlinedInvoke = outlinedInvokeBuilder.build();
            outlineBuilder.add(outlinedInvoke);
            if (invoke.hasOutValue()) {
              outlineBuilder.map(invoke.outValue(), outlinedInvoke.outValue());
            }
            continuation.accept(outlineBuilder);
          });
    }

    private void processStringBuilderAppendOrToString(
        BasicBlock throwBlock, InvokeVirtual invoke, Consumer<OutlineBuilder> continuation) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!factory.stringBuilderMethods.isAppendMethod(invokedMethod)
          && !invokedMethod.match(factory.stringBuilderMethods.toString)) {
        // Unhandled instruction.
        startOutline(invoke.getNext(), continuation);
        return;
      }
      processPredecessorInstructionOrStartOutline(
          throwBlock,
          invoke,
          outlineBuilder -> {
            InvokeVirtual.Builder outlinedInvokeBuilder =
                InvokeVirtual.builder()
                    .setArguments(
                        ListUtils.map(
                            invoke.arguments(), outlineBuilder::getOutlinedValueOrCreateArgument))
                    .setMethod(invoke.getInvokedMethod())
                    .setPosition(Position.syntheticNone());
            if (invoke.hasOutValue()) {
              outlinedInvokeBuilder.setFreshOutValue(
                  outlineBuilder.valueNumberGenerator, invoke.getOutType());
            }
            InvokeVirtual outlinedInvoke = outlinedInvokeBuilder.build();
            outlineBuilder.add(outlinedInvoke);
            if (invoke.hasOutValue()) {
              outlineBuilder.map(invoke.outValue(), outlinedInvoke.outValue());
            }
            continuation.accept(outlineBuilder);
          });
    }

    private void startOutline(
        Instruction firstOutlinedInstruction, Consumer<OutlineBuilder> continuation) {
      OutlineBuilder outlineBuilder = new OutlineBuilder(firstOutlinedInstruction);
      continuation.accept(outlineBuilder);
    }
  }

  private static class OutlineBuilder {

    private final Instruction firstOutlinedInstruction;

    private final List<Argument> outlinedArguments = new ArrayList<>();
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
      assert !instruction.isArgument();
      outlinedBlock.add(instruction, metadata);
    }

    Argument addArgument(Value value) {
      Argument outlinedArgument =
          Argument.builder()
              .setFreshOutValue(valueNumberGenerator, value.getType())
              .setIndex(outlinedArguments.size())
              .setPosition(Position.none())
              .build();
      outlinedArguments.add(outlinedArgument);
      map(value, outlinedArgument.outValue());
      return outlinedArgument;
    }

    void map(Value value, Value outlinedValue) {
      assert !outlinedValues.containsKey(value);
      outlinedValues.put(value, outlinedValue);
    }

    Instruction getFirstOutlinedInstruction() {
      return firstOutlinedInstruction;
    }

    Value getOutlinedValue(Value value) {
      return outlinedValues.get(value);
    }

    Value getOutlinedValueOrCreateArgument(Value value) {
      Value outlinedValue = getOutlinedValue(value);
      if (outlinedValue != null) {
        return outlinedValue;
      }
      return addArgument(value).outValue();
    }

    DexProto getProto(AppView<?> appView) {
      DexItemFactory factory = appView.dexItemFactory();
      DexType returnType = factory.voidType;
      return factory.createProto(
          returnType,
          ListUtils.map(
              outlinedArguments,
              outlinedArgument -> {
                TypeElement useType =
                    TypeUtils.computeUseType(
                        appView, factory.voidType, outlinedArgument.outValue());
                return DexTypeUtils.toDexType(factory, useType);
              }));
    }

    List<Value> buildArguments() {
      if (outlinedArguments.isEmpty()) {
        return Collections.emptyList();
      }
      List<Value> arguments = Arrays.asList(new Value[outlinedArguments.size()]);
      for (Entry<Value, Value> entry : outlinedValues.entrySet()) {
        Value value = entry.getKey();
        Value outlinedValue = entry.getValue();
        if (outlinedValue.isArgument()) {
          Argument outlinedArgument = outlinedValue.getDefinition().asArgument();
          arguments.set(outlinedArgument.getIndexRaw(), value);
        }
      }
      return arguments;
    }

    LirCode<?> buildLirCode(AppView<?> appView, ProgramMethod context) {
      BasicBlockInstructionListIterator outlinedBlockIterator = outlinedBlock.listIterator();
      for (Argument outlinedArgument : outlinedArguments) {
        outlinedBlockIterator.add(outlinedArgument);
      }
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
      assert lirCode.getArgumentCount() == outlinedArguments.size();
      return lirCode;
    }
  }
}
