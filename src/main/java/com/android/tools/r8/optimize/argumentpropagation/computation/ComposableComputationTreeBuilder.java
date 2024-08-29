// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import static com.android.tools.r8.ir.code.Opcodes.AND;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.OR;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.path.PathConstraintSupplier;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameterFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * Similar to {@link DefaultComputationTreeBuilder} except that this also has support for
 * int-valued, non-cyclic phis and logical OR instructions.
 */
public class ComposableComputationTreeBuilder extends ComputationTreeBuilder {

  private final PathConstraintSupplier pathConstraintSupplier;

  private final Set<Phi> seenPhis = Sets.newIdentityHashSet();

  public ComposableComputationTreeBuilder(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      ProgramMethod method,
      MethodParameterFactory methodParameterFactory,
      PathConstraintSupplier pathConstraintSupplier) {
    super(appView, code, method, methodParameterFactory);
    this.pathConstraintSupplier = pathConstraintSupplier;
  }

  @Override
  ComputationTreeNode buildComputationTree(Instruction instruction) {
    switch (instruction.opcode()) {
      case AND:
        {
          And and = instruction.asAnd();
          ComputationTreeNode left = getOrBuildComputationTree(and.leftValue());
          ComputationTreeNode right = getOrBuildComputationTree(and.rightValue());
          return ComputationTreeLogicalBinopAndNode.create(left, right);
        }
      case ARGUMENT:
        {
          Argument argument = instruction.asArgument();
          if (argument.getOutType().isInt()) {
            return methodParameterFactory.create(method, argument.getIndex());
          }
          break;
        }
      case CONST_NUMBER:
        {
          ConstNumber constNumber = instruction.asConstNumber();
          if (constNumber.getOutType().isInt()) {
            return constNumber.getAbstractValue(factory());
          }
          break;
        }
      case IF:
        {
          If theIf = instruction.asIf();
          if (theIf.isZeroTest()) {
            ComputationTreeNode operand = getOrBuildComputationTree(theIf.lhs());
            return ComputationTreeUnopCompareNode.create(operand, theIf.getType());
          }
          break;
        }
      case OR:
        {
          Or or = instruction.asOr();
          ComputationTreeNode left = getOrBuildComputationTree(or.leftValue());
          ComputationTreeNode right = getOrBuildComputationTree(or.rightValue());
          return ComputationTreeLogicalBinopOrNode.create(left, right);
        }
      default:
        break;
    }
    return unknown();
  }

  @Override
  ComputationTreeNode buildComputationTree(Phi phi) {
    if (!seenPhis.add(phi) || phi.getOperands().size() != 2 || !phi.getType().isInt()) {
      return unknown();
    }
    ComputationTreeNode left = getOrBuildComputationTree(phi.getOperand(0));
    ComputationTreeNode right = getOrBuildComputationTree(phi.getOperand(1));
    if (left.isUnknown() && right.isUnknown()) {
      return unknown();
    }
    BasicBlock block = phi.getBlock();
    ComputationTreeNode condition =
        pathConstraintSupplier.getDifferentiatingPathConstraint(
            block.getPredecessor(0), block.getPredecessor(1));
    return ComputationTreeLogicalBinopIntPhiNode.create(condition, left, right);
  }
}
