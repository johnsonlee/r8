// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import static com.android.tools.r8.ir.code.Opcodes.AND;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.IF;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldValueFactory;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameterFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class DefaultComputationTreeBuilder extends ComputationTreeBuilder {

  public DefaultComputationTreeBuilder(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      ProgramMethod method,
      FieldValueFactory fieldValueFactory,
      MethodParameterFactory methodParameterFactory) {
    super(appView, code, method, fieldValueFactory, methodParameterFactory);
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
      default:
        break;
    }
    return unknown();
  }

  @Override
  ComputationTreeNode buildComputationTree(Phi phi) {
    return unknown();
  }
}
