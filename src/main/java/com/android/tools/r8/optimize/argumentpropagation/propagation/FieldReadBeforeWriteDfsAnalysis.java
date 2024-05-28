// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.GOTO;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.THROW;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockInstructionIterator;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramFieldSet;

/**
 * Analysis that is given an allocation site (a {@link NewInstance} instruction) and a set of fields
 * that belong to that newly allocated instance.
 *
 * <p>The analysis computes the subset of the given fields which are maybe read before they are
 * written. The default value of these fields is potentially read, whereas the default value of the
 * complement field set are guaranteed to never be read.
 *
 * <p>The analysis works by exploring all possible paths starting from the given allocation site to
 * the normal and the exceptional exits of the method, keeping track of which fields are definitely
 * written before they are read and which fields have maybe been read.
 */
public abstract class FieldReadBeforeWriteDfsAnalysis extends FieldReadBeforeWriteDfsAnalysisState {

  private final AppView<AppInfoWithLiveness> appView;
  private final IRCode code;
  private final DexItemFactory dexItemFactory;
  // The set of fields to consider. Note that this is a concurrent set and that the caller may
  // concurrently remove fields from the set. This may happen if we concurrently find a
  // read-before-write of one of the fields.
  private final ProgramFieldSet fields;
  private final WorkList<WorkItem> worklist = WorkList.newIdentityWorkList();

  private final FieldReadBeforeWriteDfsAnalysis self = this;

  public FieldReadBeforeWriteDfsAnalysis(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      ProgramFieldSet fields,
      NewInstance newInstance) {
    super(newInstance);
    this.appView = appView;
    this.code = code;
    this.dexItemFactory = appView.dexItemFactory();
    this.fields = fields;
  }

  // Returns ABORT if all fields of interest are now maybe-read-before-written.
  // Otherwise returns CONTINUE.
  public abstract AnalysisContinuation acceptFieldMaybeReadBeforeWrite(ProgramField field);

  public void run() {
    worklist.addIfNotSeen(new InitialWorkItem());
    worklist.run(WorkItem::process);
  }

  public enum AnalysisContinuation {
    // Signals to abort the analysis completely (i.e., to break out of the DFS). This is used when
    // we've reported all fields as being maybe read before written.
    ABORT,
    // Signals to continue the current DFS.
    CONTINUE,
    // Signals that all fields have been written before they are read on the current program path,
    // meaning that the algorithm does not need to explore any further. The algorithm should instead
    // backtrack and explore any other program paths.
    BREAK;

    static AnalysisContinuation abortIf(boolean condition) {
      if (condition) {
        return ABORT;
      }
      return CONTINUE;
    }

    boolean isAbort() {
      return this == ABORT;
    }

    boolean isAbortOrContinue() {
      return isAbort() || isContinue();
    }

    boolean isBreak() {
      return this == BREAK;
    }

    boolean isContinue() {
      return this == CONTINUE;
    }

    TraversalContinuation<?, ?> toTraversalContinuation() {
      assert isAbortOrContinue();
      return TraversalContinuation.breakIf(isAbort());
    }
  }

  abstract class WorkItem {

    abstract TraversalContinuation<?, ?> process();

    void applyPhis(BasicBlock block) {
      // TODO(b/339210038): When adding support for non-linear control flow, we need to implement
      //  backtracking of this (i.e., we should remove the out value from the object aliases again).
      for (Phi phi : block.getPhis()) {
        if (phi.hasOperandThatMatches(self::isMaybeInstance)) {
          addInstanceAlias(phi);
        }
      }
    }

    AnalysisContinuation applyInstructions(BasicBlockInstructionIterator instructionIterator) {
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        assert !instruction.hasOutValue() || !isMaybeInstance(instruction.outValue());
        AnalysisContinuation continuation;
        // TODO(b/339210038): Extend this to many other instructions, such as ConstClass,
        //  InstanceOf, *Binop, etc.
        switch (instruction.opcode()) {
          case ASSUME:
            continuation = applyAssumeInstruction(instruction.asAssume());
            break;
          case CHECK_CAST:
            continuation = applyCheckCastInstruction(instruction.asCheckCast());
            break;
          case CONST_NUMBER:
            continuation = applyConstNumber(instruction.asConstNumber());
            break;
          case CONST_STRING:
            continuation = applyConstString(instruction.asConstString());
            break;
          case GOTO:
            continuation = applyGotoInstruction(instruction.asGoto());
            break;
          case INSTANCE_PUT:
            continuation = applyInstancePut(instruction.asInstancePut());
            break;
          case INVOKE_DIRECT:
          case INVOKE_INTERFACE:
          case INVOKE_STATIC:
          case INVOKE_SUPER:
          case INVOKE_VIRTUAL:
            continuation = applyInvokeMethodInstruction(instruction.asInvokeMethod());
            break;
          case RETURN:
            continuation = applyReturnInstruction(instruction.asReturn());
            break;
          case THROW:
            continuation = applyThrowInstruction(instruction.asThrow());
            break;
          default:
            continuation = applyUnhandledInstruction();
            break;
        }
        if (continuation.isAbort()) {
          return continuation;
        }
        if (continuation.isBreak()) {
          break;
        }
      }
      return AnalysisContinuation.CONTINUE;
    }

    // TODO(b/339210038): When adding support for non-linear control flow, we need to implement
    //  backtracking of this (i.e., we should remove the out value from the object aliases again).
    private AnalysisContinuation applyAssumeInstruction(Assume assume) {
      if (isMaybeInstance(assume.src())) {
        addInstanceAlias(assume.outValue());
      }
      return AnalysisContinuation.CONTINUE;
    }

    // TODO(b/339210038): When adding support for non-linear control flow, we need to implement
    //  backtracking of this (i.e., we should remove the out value from the object aliases again).
    private AnalysisContinuation applyCheckCastInstruction(CheckCast checkCast) {
      if (isMaybeInstance(checkCast.object())) {
        addInstanceAlias(checkCast.outValue());
      }
      // If the instance has escaped to the heap and this check-cast instruction throws, then it is
      // possible that the instance is retrieved from the heap and all fields are read.
      return markRemainingFieldsAsMaybeReadBeforeWrittenIfInstanceIsEscaped();
    }

    private AnalysisContinuation applyConstNumber(ConstNumber unusedConstNumber) {
      return AnalysisContinuation.CONTINUE;
    }

    private AnalysisContinuation applyConstString(ConstString unusedConstString) {
      return AnalysisContinuation.CONTINUE;
    }

    private AnalysisContinuation applyGotoInstruction(Goto gotoInstruction) {
      BasicBlock targetBlock = gotoInstruction.getTarget();
      if (isBlockOnStack(targetBlock)) {
        // Bail out in case of cycles.
        return markRemainingFieldsAsMaybeReadBeforeWritten();
      } else {
        // Continue exploration into the successor block.
        worklist.addIgnoringSeenSet(new ProcessBlockWorkItem(targetBlock));
        return AnalysisContinuation.CONTINUE;
      }
    }

    private AnalysisContinuation applyInstancePut(InstancePut instancePut) {
      // If the instance has escaped and this instance-put instruction can throw, then the program
      // can get the instance from the heap and read any field. Give up in this case.
      if (isEscaped() && instancePut.instructionInstanceCanThrow(appView, code.context())) {
        return markRemainingFieldsAsMaybeReadBeforeWritten();
      }

      // Record if this is a definite write to one of the fields of interest.
      if (isDefinitelyInstance(instancePut.object())) {
        ProgramField resolvedField =
            instancePut.resolveField(appView, code.context()).getProgramField();
        if (resolvedField != null && fields.contains(resolvedField)) {
          addWrittenBeforeRead(resolvedField);
        }

        // If all fields of interest are written before read, then stop the exploration of the
        // current program path (but continue to explore any program paths from previous unexplored
        // branches).
        if (fields.allMatch(self::isWrittenBeforeRead)) {
          return AnalysisContinuation.BREAK;
        }
      }

      // Record if the instance has escaped as a result of this instance-put.
      if (!isEscaped() && isMaybeInstance(instancePut.value())) {
        setEscaped(instancePut);
      }
      return AnalysisContinuation.CONTINUE;
    }

    private AnalysisContinuation applyInvokeMethodInstruction(InvokeMethod invoke) {
      // Allow calls to java.lang.Object.<init>().
      // TODO(b/339210038): Generalize this to other constructors.
      if (invoke.isInvokeConstructor(dexItemFactory)
          && isDefinitelyInstance(invoke.getFirstArgument())) {
        DexClassAndMethod resolvedMethod =
            invoke.resolveMethod(appView, code.context()).getResolutionPair();
        if (resolvedMethod != null
            && resolvedMethod
                .getReference()
                .isIdenticalTo(dexItemFactory.objectMembers.constructor)) {
          return AnalysisContinuation.CONTINUE;
        }
      }

      // Conservatively treat calls as reading any field if the receiver has escaped or is escaping.
      if (!isEscaped()
          && invoke.hasInValueThatMatches(self::isMaybeInstance)
          && invoke.instructionMayHaveSideEffects(appView, code.context())) {
        setEscaped(invoke);
      }

      if (isEscaped()) {
        return markRemainingFieldsAsMaybeReadBeforeWritten();
      }

      // Otherwise, this is a call to a method where none of the arguments is an alias of the
      // instance, and the instance has not escaped. Therefore, this call cannot read any of fields
      // from the instance.
      return AnalysisContinuation.CONTINUE;
    }

    private AnalysisContinuation applyReturnInstruction(Return unusedReturnInstruction) {
      return markRemainingFieldsAsMaybeReadBeforeWritten();
    }

    private AnalysisContinuation applyThrowInstruction(Throw unusedThrowInstruction) {
      return markRemainingFieldsAsMaybeReadBeforeWrittenIfInstanceIsEscaped();
    }

    private AnalysisContinuation applyUnhandledInstruction() {
      return markRemainingFieldsAsMaybeReadBeforeWritten();
    }

    AnalysisContinuation markRemainingFieldsAsMaybeReadBeforeWritten() {
      for (ProgramField field : fields) {
        if (!isWrittenBeforeRead(field)) {
          AnalysisContinuation continuation = acceptFieldMaybeReadBeforeWrite(field);
          assert continuation.isAbortOrContinue();
          if (continuation.isAbort()) {
            return continuation;
          }
        }
      }
      // At this point we could also CONTINUE, but we check if the fields of interest have become
      // empty as a result of concurrent modification.
      return AnalysisContinuation.abortIf(fields.isEmpty());
    }

    AnalysisContinuation markRemainingFieldsAsMaybeReadBeforeWrittenIfInstanceIsEscaped() {
      if (isEscaped()) {
        return markRemainingFieldsAsMaybeReadBeforeWritten();
      }
      return AnalysisContinuation.CONTINUE;
    }
  }

  class InitialWorkItem extends WorkItem {

    @Override
    TraversalContinuation<?, ?> process() {
      // We start the analysis from the unique constructor invoke instead of from the NewInstance
      // instruction, since no instructions before the constructor call can read any fields from the
      // uninitialized this.
      // TODO(b/339210038): In principle it may be possible for the NewInstance value to flow into a
      //  phi before the unique constructor invoke. If this happens we would not record the phi as
      //  an alias when starting the analysis from the invoke-direct.
      InvokeDirect uniqueConstructorInvoke =
          getNewInstance().getUniqueConstructorInvoke(dexItemFactory);
      if (uniqueConstructorInvoke == null) {
        return markRemainingFieldsAsMaybeReadBeforeWritten().toTraversalContinuation();
      }
      BasicBlock block = uniqueConstructorInvoke.getBlock();
      // TODO(b/339210038): Maybe allow exceptional control flow.
      if (block.hasCatchHandlers()) {
        return markRemainingFieldsAsMaybeReadBeforeWritten().toTraversalContinuation();
      }
      addBlockToStack(block);
      addInstanceAlias(getNewInstance().outValue());
      BasicBlockInstructionIterator instructionIterator = block.iterator(uniqueConstructorInvoke);
      // Start the analysis from the invoke-direct instruction. This is important if we can tell
      // that the constructor definitely writes some fields.
      instructionIterator.previous();
      return applyInstructions(instructionIterator).toTraversalContinuation();
    }
  }

  class ProcessBlockWorkItem extends WorkItem {

    private final BasicBlock block;

    ProcessBlockWorkItem(BasicBlock block) {
      this.block = block;
    }

    @Override
    TraversalContinuation<?, ?> process() {
      // TODO(b/339210038): Maybe allow exceptional control flow.
      if (block.hasCatchHandlers()) {
        return TraversalContinuation.breakIf(
            markRemainingFieldsAsMaybeReadBeforeWritten().isAbort());
      }
      addBlockToStack(block);
      applyPhis(block);
      AnalysisContinuation continuation = applyInstructions(block.iterator());
      assert continuation.isAbortOrContinue();
      return TraversalContinuation.breakIf(continuation.isAbort());
    }
  }
}
