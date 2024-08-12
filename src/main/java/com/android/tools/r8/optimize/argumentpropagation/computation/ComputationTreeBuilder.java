// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import static com.android.tools.r8.ir.code.Opcodes.AND;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.IF;

import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;

public class ComputationTreeBuilder {

  private final AbstractValueFactory abstractValueFactory;

  public ComputationTreeBuilder(AbstractValueFactory abstractValueFactory) {
    this.abstractValueFactory = abstractValueFactory;
  }

  // TODO(b/302281503): "Long lived" computation trees (i.e., the ones that survive past the IR
  //  conversion of the current method) should be canonicalized.
  // TODO(b/302281503): If we start building larger computation trees then make sure to the
  //  computation trees for intermediate instructions to ensure that we do not build the computation
  //  tree for a given instruction more than once.
  public ComputationTreeNode buildComputationTree(Instruction instruction) {
    switch (instruction.opcode()) {
      case AND:
        {
          And and = instruction.asAnd();
          ComputationTreeNode left = buildComputationTreeFromValue(and.leftValue());
          ComputationTreeNode right = buildComputationTreeFromValue(and.rightValue());
          return ComputationTreeLogicalBinopAndNode.create(left, right);
        }
      case ARGUMENT:
        {
          Argument argument = instruction.asArgument();
          if (argument.getOutType().isInt()) {
            return ComputationTreeArgumentNode.create(argument.getIndex());
          }
          break;
        }
      case CONST_NUMBER:
        {
          ConstNumber constNumber = instruction.asConstNumber();
          if (constNumber.getOutType().isInt()) {
            return constNumber.getAbstractValue(abstractValueFactory);
          }
          break;
        }
      case IF:
        {
          If theIf = instruction.asIf();
          if (theIf.isZeroTest()) {
            ComputationTreeNode operand = buildComputationTreeFromValue(theIf.lhs());
            return ComputationTreeUnopCompareNode.create(operand, theIf.getType());
          }
          break;
        }
      default:
        break;
    }
    return AbstractValue.unknown();
  }

  private ComputationTreeNode buildComputationTreeFromValue(Value value) {
    if (value.isPhi()) {
      return unknown();
    }
    return buildComputationTree(value.getDefinition());
  }

  private static UnknownValue unknown() {
    return AbstractValue.unknown();
  }
}
