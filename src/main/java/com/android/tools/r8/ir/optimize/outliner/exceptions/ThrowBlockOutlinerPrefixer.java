// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.StringBuildingMethods;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionList;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import java.util.Set;

public class ThrowBlockOutlinerPrefixer {

  private final DexItemFactory factory;
  private final BasicBlock block;
  private final Instruction previousOutlineEnd;

  ThrowBlockOutlinerPrefixer(
      DexItemFactory factory, BasicBlock block, Instruction previousOutlineEnd) {
    this.factory = factory;
    this.block = block;
    this.previousOutlineEnd = previousOutlineEnd;
  }

  /**
   * Tries to move non-outlined instructions into the outline.
   *
   * @return The first instruction of the outline.
   */
  Instruction tryMoveNonOutlinedStringBuilderInstructionsToOutline(
      Instruction firstOutlinedInstruction) {
    Value stringBuilder = getOutlinedStringBuilder(firstOutlinedInstruction);
    if (stringBuilder == null) {
      return firstOutlinedInstruction;
    }

    // If the StringBuilder escapes, then give up, since the state of the StringBuilder may be
    // observed from the outside.
    Set<Value> stringBuilderAliases = getAliasesForNonEscapingStringBuilder(stringBuilder);
    if (stringBuilderAliases == null) {
      return firstOutlinedInstruction;
    }

    // Try to move calls into the outline.
    return processNonEscapingStringBuilder(firstOutlinedInstruction, stringBuilderAliases);
  }

  /**
   * Returns for receiver of the first call to StringBuilder.append() or StringBuilder.toString() in
   * the outline.
   */
  private Value getOutlinedStringBuilder(Instruction firstOutlinedInstruction) {
    for (Instruction instruction = firstOutlinedInstruction;
        instruction != null;
        instruction = instruction.getNext()) {
      InvokeVirtual invoke = instruction.asInvokeVirtual();
      if (invoke != null) {
        DexMethod invokedMethod = invoke.getInvokedMethod();
        StringBuildingMethods stringBuilderMethods = factory.stringBuilderMethods;
        if (stringBuilderMethods.isAppendMethod(invokedMethod)
            || invokedMethod.isIdenticalTo(stringBuilderMethods.toString)
            || (invoke.getReceiver().getType().isClassType(factory.stringBuilderType)
                && invokedMethod.match(stringBuilderMethods.toString))) {
          return invoke.getReceiver();
        }
      }
    }
    return null;
  }

  /**
   * Performs a simple escape analysis for the given StringBuilder, which checks that:
   *
   * <ul>
   *   <li>The StringBuilder is only used as the receiver in calls to {@link StringBuilder#append}
   *       or {@link StringBuilder#toString()}.
   * </ul>
   *
   * <p>The analysis accounts for aliases introduced by calls to {@link StringBuilder#append}, which
   * may return a new SSA value that aliases the receiver of the call.
   *
   * @return All the (definite) aliases of the given StringBuilder value.
   */
  private Set<Value> getAliasesForNonEscapingStringBuilder(Value stringBuilder) {
    // Find the root value that is defined by a new-instance instruction.
    // Use a seen set to guard against cycles.
    Set<Value> seen = SetUtils.newIdentityHashSet();
    Value stringBuilderRoot = getStringBuilderRoot(stringBuilder, seen);
    if (stringBuilderRoot == null) {
      return null;
    }
    assert stringBuilderRoot.isDefinedByInstructionSatisfying(Instruction::isNewInstance);
    assert stringBuilderRoot.getType().isClassType(factory.stringBuilderType);

    // Check that all transitive uses of the root StringBuilder are append() calls or toString()
    // calls.
    seen.clear();
    WorkList<Value> worklist = WorkList.newWorkList(seen);
    worklist.addIfNotSeen(stringBuilderRoot);
    while (worklist.hasNext()) {
      Value stringBuilderAlias = worklist.next();
      if (stringBuilderAlias.hasPhiUsers()) {
        return null;
      }
      for (Instruction user : stringBuilderAlias.uniqueUsers()) {
        if (user.isInvokeConstructor(factory)) {
          // Fallthrough.
        } else if (user.isInvokeVirtual()) {
          InvokeVirtual invoke = user.asInvokeVirtual();
          DexMethod invokedMethod = invoke.getInvokedMethod();
          if (factory.stringBuilderMethods.isAppendMethod(invokedMethod)) {
            if (invoke.hasOutValue()) {
              // Also analyze the uses of the out-value.
              worklist.addIfNotSeen(invoke.outValue());
            }
          } else if (!invokedMethod.match(factory.stringBuilderMethods.toString)) {
            return null;
          }
          // Fallthrough.
        } else {
          return null;
        }
        if (user.inValues().lastIndexOf(stringBuilderAlias) != 0) {
          return null;
        }
      }
    }
    return seen;
  }

  /**
   * Finds the root StringBuilder value that is defined by a new-instance instruction, by traversing
   * up the aliases through the append() call chain.
   */
  private Value getStringBuilderRoot(Value stringBuilder, Set<Value> seen) {
    assert seen.isEmpty();
    while (true) {
      if (stringBuilder.isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
        // If it is defined by a new-instance instruction, we found the root.
        return stringBuilder;
      } else if (stringBuilder.isDefinedByInstructionSatisfying(Instruction::isInvokeVirtual)) {
        // If it is defined by an append instruction, then continue the traversal from the
        // receiver.
        InvokeVirtual invoke = stringBuilder.getDefinition().asInvokeVirtual();
        if (seen.add(stringBuilder)
            && factory.stringBuilderMethods.isAppendMethod(invoke.getInvokedMethod())) {
          stringBuilder = invoke.getReceiver();
          continue;
        }
      }
      return null;
    }
  }

  private Instruction processNonEscapingStringBuilder(
      Instruction firstOutlinedInstruction, Set<Value> stringBuilderAliases) {
    // Iterate backwards starting from the first outlined instruction and move eligible
    // StringBuilder instructions into the outline.
    InvokeDirect stringBuilderConstructorCall = null;
    for (Instruction instruction = firstOutlinedInstruction.getPrev();
        instruction != null && instruction != previousOutlineEnd;
        instruction = instruction.getPrev()) {
      switch (instruction.opcode()) {
        case INVOKE_DIRECT:
          {
            InvokeDirect invoke = instruction.asInvokeDirect();
            if (stringBuilderAliases.contains(invoke.getReceiver())) {
              DexMethod invokedMethod = invoke.getInvokedMethod();
              // This is guaranteed to be a constructor call by the escape analysis.
              assert invoke.isInvokeConstructor(factory);
              if (!invokedMethod.isIdenticalTo(factory.stringBuilderMethods.defaultConstructor)
                  && !invokedMethod.isIdenticalTo(factory.stringBuilderMethods.stringConstructor)) {
                // Unhandled constructor call.
                return firstOutlinedInstruction;
              }
              // We cannot outline the constructor call independently of the new-instance
              // instruction,
              // so we record that it should be moved along with the new-instance instruction.
              stringBuilderConstructorCall = invoke;
            } else {
              assert verifyInstructionDoesNotUseStringBuilder(instruction, stringBuilderAliases);
            }
            break;
          }

        case INVOKE_VIRTUAL:
          {
            InvokeVirtual invoke = instruction.asInvokeVirtual();
            if (stringBuilderAliases.contains(invoke.getReceiver())) {
              DexMethod invokedMethod = invoke.getInvokedMethod();
              if (!factory.stringBuilderMethods.isAppendPrimitiveMethod(invokedMethod)
                  && !factory.stringBuilderMethods.isAppendStringMethod(invokedMethod)) {
                // Unhandled method call.
                return firstOutlinedInstruction;
              }
              firstOutlinedInstruction =
                  moveNonOutlinedInstructionToOutline(invoke, firstOutlinedInstruction);
            } else {
              assert verifyInstructionDoesNotUseStringBuilder(instruction, stringBuilderAliases);
            }
            break;
          }

        case NEW_INSTANCE:
          {
            NewInstance newInstance = instruction.asNewInstance();
            if (stringBuilderAliases.contains(newInstance.outValue())) {
              assert newInstance.getType().isIdenticalTo(factory.stringBuilderType);
              if (stringBuilderConstructorCall != null) {
                firstOutlinedInstruction =
                    moveNonOutlinedInstructionToOutline(
                        stringBuilderConstructorCall, firstOutlinedInstruction);
              }
              return moveNonOutlinedInstructionToOutline(newInstance, firstOutlinedInstruction);
            }
            break;
          }

        default:
          {
            // This instruction does not use the StringBuilder since the StringBuilder is
            // non-escaping, so we can safely continue.
            assert verifyInstructionDoesNotUseStringBuilder(instruction, stringBuilderAliases);
            break;
          }
      }
    }
    return firstOutlinedInstruction;
  }

  /**
   * Detaches the {@param nonOutlinedInstruction} from the IR and inserts it right before the
   * {@param firstOutlinedInstruction}.
   *
   * @return The {@param nonOutlinedInstruction}, which is now the new first outlined instruction.
   */
  private Instruction moveNonOutlinedInstructionToOutline(
      Instruction nonOutlinedInstruction, Instruction firstOutlinedInstruction) {
    if (nonOutlinedInstruction != firstOutlinedInstruction.getPrev()) {
      InstructionList throwBlockInstructions = block.getInstructions();
      throwBlockInstructions.removeIgnoreValues(nonOutlinedInstruction);
      throwBlockInstructions.addBefore(nonOutlinedInstruction, firstOutlinedInstruction);
    }
    return nonOutlinedInstruction;
  }

  private boolean verifyInstructionDoesNotUseStringBuilder(
      Instruction instruction, Set<Value> stringBuilderAliases) {
    assert instruction.inValues().stream().noneMatch(stringBuilderAliases::contains);
    return true;
  }
}
