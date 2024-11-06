// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import static com.android.tools.r8.ir.code.Opcodes.GOTO;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.STRING_SWITCH;
import static com.android.tools.r8.ir.code.Opcodes.THROW;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.StringSwitch;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Analysis that given a method computes a constraint which is satisfied by a concrete call site
 * only if the method becomes simple after inlining into the concrete call site.
 *
 * <p>Examples of simple inlining constraints are:
 *
 * <ul>
 *   <li>Always simple,
 *   <li>Never simple,
 *   <li>Simple if argument i is {true, false, null, not-null},
 *   <li>Simple if argument i is true and argument j is false, or if argument i is false.
 * </ul>
 */
public class SimpleInliningConstraintAnalysis {

  private static final int MAX_BRANCH_DEPTH = 3;

  private final SimpleInliningConstraintFactory constraintFactory;
  private final DexItemFactory dexItemFactory;
  private final ProgramMethod method;
  private final InternalOptions options;
  private final int simpleInliningConstraintThreshold;

  private final Set<BasicBlock> seen = Sets.newIdentityHashSet();

  public SimpleInliningConstraintAnalysis(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method) {
    this.constraintFactory = appView.simpleInliningConstraintFactory();
    this.dexItemFactory = appView.dexItemFactory();
    this.method = method;
    this.options = appView.options();
    this.simpleInliningConstraintThreshold = appView.options().simpleInliningConstraintThreshold;
  }

  public SimpleInliningConstraintWithDepth analyzeCode(IRCode code) {
    if (method.getReference().getArity() == 0) {
      // The method does not have any parameters, so there is no need to analyze the method.
      return SimpleInliningConstraintWithDepth.getNever();
    }

    if (options.debug) {
      // Inlining is not enabled in debug mode.
      return SimpleInliningConstraintWithDepth.getNever();
    }

    // Run a bounded depth-first traversal to collect the path constraints that lead to early
    // returns.
    BasicBlock block = code.entryBlock();
    Instruction firstNonArgument = block.getInstructions().getNth(code.getNumberOfArguments());
    return analyzeInstructionsInBlock(block, 0, 0, firstNonArgument);
  }

  private SimpleInliningConstraintWithDepth analyzeInstructionsInBlock(
      BasicBlock block, int branchDepth, int instructionDepth) {
    return analyzeInstructionsInBlock(block, branchDepth, instructionDepth, block.entry());
  }

  private SimpleInliningConstraintWithDepth analyzeInstructionsInBlock(
      BasicBlock block, int branchDepth, int instructionDepth, Instruction instruction) {
    if (!seen.add(block)
        || block.hasCatchHandlers()
        || block.exit().isThrow()
        || branchDepth > MAX_BRANCH_DEPTH) {
      return SimpleInliningConstraintWithDepth.getNever();
    }

    // Move the instruction iterator forward to the block's jump instruction, while incrementing the
    // instruction depth of the depth-first traversal.
    SimpleInliningConstraint blockConstraint = AlwaysSimpleInliningConstraint.getInstance();
    while (!instruction.isJumpInstruction()) {
      assert !instruction.isArgument();
      assert !instruction.isDebugInstruction();
      SimpleInliningConstraint instructionConstraint =
          computeConstraintForInstructionNotToMaterialize(instruction);
      if (instructionConstraint.isAlways()) {
        assert instruction.isAssume() || instruction.isConstInstruction();
      } else if (instructionConstraint.isNever()) {
        instructionDepth++;
      } else {
        blockConstraint = blockConstraint.meet(instructionConstraint);
      }
      instruction = instruction.getNext();
    }

    // If we have exceeded the threshold, then all paths from this instruction will not lead to any
    // early exits, so return 'never'.
    if (instructionDepth > simpleInliningConstraintThreshold) {
      return SimpleInliningConstraintWithDepth.getNever();
    }

    SimpleInliningConstraintWithDepth jumpConstraint =
        computeConstraintForJumpInstruction(
            instruction.asJumpInstruction(), branchDepth, instructionDepth);
    return jumpConstraint.meet(blockConstraint);
  }

  private SimpleInliningConstraint computeConstraintForInstructionNotToMaterialize(
      Instruction instruction) {
    if (instruction.isAssume()) {
      return AlwaysSimpleInliningConstraint.getInstance();
    }
    // We treat const instructions as non-materializing, although they may actually materialize.
    // In practice, since we limit the number of materializing instructions to one, only few
    // constants should remain after inlining (e.g., if the materializing instruction is an invoke
    // that uses constants as in-values).
    if (instruction.isConstInstruction()) {
      return AlwaysSimpleInliningConstraint.getInstance();
    }
    if (instruction.isInvokeVirtual()) {
      InvokeVirtual invoke = instruction.asInvokeVirtual();
      if (invoke.getInvokedMethod().isIdenticalTo(dexItemFactory.objectMembers.getClass)
          && invoke.hasUnusedOutValue()) {
        Value receiver = invoke.getReceiver();
        if (receiver.getType().isDefinitelyNotNull()) {
          return AlwaysSimpleInliningConstraint.getInstance();
        }
        Value receiverRoot = receiver.getAliasedValue();
        if (receiverRoot.isDefinedByInstructionSatisfying(Instruction::isArgument)) {
          Argument argument = receiverRoot.getDefinition().asArgument();
          return constraintFactory.createNotEqualToNullConstraint(argument.getIndex());
        }
      }
    }
    return NeverSimpleInliningConstraint.getInstance();
  }

  private SimpleInliningConstraintWithDepth computeConstraintForJumpInstruction(
      JumpInstruction instruction, int branchDepth, int instructionDepth) {
    switch (instruction.opcode()) {
      case IF:
        {
          If ifInstruction = instruction.asIf();
          Value singleArgumentOperand = getSingleArgumentOperand(ifInstruction);
          if (singleArgumentOperand == null || singleArgumentOperand.isThis()) {
            break;
          }

          Value otherOperand =
              ifInstruction.isZeroTest()
                  ? null
                  : ifInstruction.getOperand(
                      1 - ifInstruction.inValues().indexOf(singleArgumentOperand));

          int argumentIndex =
              singleArgumentOperand.getAliasedValue().getDefinition().asArgument().getIndex();
          DexType argumentType = method.getDefinition().getArgumentType(argumentIndex);
          int currentBranchDepth = branchDepth;
          int currentInstructionDepth = instructionDepth;

          // Compute the constraint for which paths through the true target are guaranteed to exit
          // early.
          int newBranchDepth = currentBranchDepth + 1;
          SimpleInliningConstraintWithDepth trueTargetConstraint =
              computeConstraintFromIfTest(
                      argumentIndex, argumentType, otherOperand, ifInstruction.getType())
                  // Only recurse into the true target if the constraint from the if-instruction
                  // is not 'never'.
                  .lazyMeet(
                      () ->
                          analyzeInstructionsInBlock(
                              ifInstruction.getTrueTarget(),
                              newBranchDepth,
                              currentInstructionDepth));

          // Compute the constraint for which paths through the false target are guaranteed to
          // exit early.
          SimpleInliningConstraintWithDepth fallthroughTargetConstraint =
              computeConstraintFromIfTest(
                      argumentIndex, argumentType, otherOperand, ifInstruction.getType().inverted())
                  // Only recurse into the false target if the constraint from the if-instruction
                  // is not 'never'.
                  .lazyMeet(
                      () ->
                          analyzeInstructionsInBlock(
                              ifInstruction.fallthroughBlock(),
                              newBranchDepth,
                              currentInstructionDepth));

          // Paths going through this basic block are guaranteed to exit early if the true target
          // is guaranteed to exit early or the false target is.
          return trueTargetConstraint.join(fallthroughTargetConstraint);
        }

      case GOTO:
        return analyzeInstructionsInBlock(
            instruction.asGoto().getTarget(), branchDepth, instructionDepth);

      case RETURN:
        return AlwaysSimpleInliningConstraint.getInstance().withDepth(instructionDepth);

      case STRING_SWITCH:
        {
          // Require that all cases including the default case are simple. In that case we can
          // guarantee simpleness by requiring that the switch value is constant.
          StringSwitch stringSwitch = instruction.asStringSwitch();
          Value valueRoot = stringSwitch.value().getAliasedValue();
          if (!valueRoot.isDefinedByInstructionSatisfying(Instruction::isArgument)) {
            return SimpleInliningConstraintWithDepth.getNever();
          }
          int newBranchDepth = branchDepth + 1;
          int maxInstructionDepth = instructionDepth;
          for (BasicBlock successor : stringSwitch.getBlock().getNormalSuccessors()) {
            SimpleInliningConstraintWithDepth successorConstraintWithDepth =
                analyzeInstructionsInBlock(successor, newBranchDepth, instructionDepth);
            if (!successorConstraintWithDepth.getConstraint().isAlways()) {
              return SimpleInliningConstraintWithDepth.getNever();
            }
            maxInstructionDepth =
                Math.max(maxInstructionDepth, successorConstraintWithDepth.getInstructionDepth());
          }
          Argument argument = valueRoot.getDefinition().asArgument();
          ConstSimpleInliningConstraint simpleConstraint =
              constraintFactory.createConstConstraint(argument.getIndex());
          return simpleConstraint.withDepth(maxInstructionDepth);
        }

      case THROW:
        return instruction.getBlock().hasCatchHandlers()
            ? SimpleInliningConstraintWithDepth.getNever()
            : SimpleInliningConstraintWithDepth.getAlways(instructionDepth);

      default:
        break;
    }

    // Give up.
    return SimpleInliningConstraintWithDepth.getNever();
  }

  private SimpleInliningConstraint computeConstraintFromIfTest(
      int argumentIndex, DexType argumentType, Value otherOperand, IfType type) {
    boolean isZeroTest = otherOperand == null;
    switch (type) {
      case EQ:
        if (isZeroTest) {
          if (argumentType.isReferenceType()) {
            return constraintFactory.createEqualToNullConstraint(argumentIndex);
          }
          if (argumentType.isBooleanType()) {
            return constraintFactory.createEqualToFalseConstraint(argumentIndex);
          }
        } else if (argumentType.isPrimitiveType()) {
          OptionalLong rawValue = getRawNumberValue(otherOperand);
          if (rawValue.isPresent()) {
            return constraintFactory.createEqualToNumberConstraint(
                argumentIndex, rawValue.getAsLong());
          }
        }
        return NeverSimpleInliningConstraint.getInstance();

      case NE:
        if (isZeroTest) {
          if (argumentType.isReferenceType()) {
            return constraintFactory.createNotEqualToNullConstraint(argumentIndex);
          }
          if (argumentType.isBooleanType()) {
            return constraintFactory.createEqualToTrueConstraint(argumentIndex);
          }
        } else if (argumentType.isPrimitiveType()) {
          OptionalLong rawValue = getRawNumberValue(otherOperand);
          if (rawValue.isPresent()) {
            return constraintFactory.createNotEqualToNumberConstraint(
                argumentIndex, rawValue.getAsLong());
          }
        }
        return NeverSimpleInliningConstraint.getInstance();

      default:
        return NeverSimpleInliningConstraint.getInstance();
    }
  }

  private OptionalLong getRawNumberValue(Value value) {
    Value root = value.getAliasedValue();
    if (root.isDefinedByInstructionSatisfying(Instruction::isConstNumber)) {
      return OptionalLong.of(root.getDefinition().asConstNumber().getRawValue());
    }
    return OptionalLong.empty();
  }

  private Value getSingleArgumentOperand(If ifInstruction) {
    Value singleArgumentOperand = null;

    Value lhs = ifInstruction.lhs();
    if (lhs.getAliasedValue().isArgument()) {
      singleArgumentOperand = lhs;
    }

    if (!ifInstruction.isZeroTest()) {
      Value rhs = ifInstruction.rhs();
      if (rhs.getAliasedValue().isArgument()) {
        if (singleArgumentOperand != null) {
          return null;
        }
        singleArgumentOperand = rhs;
      }
    }

    return singleArgumentOperand;
  }
}
