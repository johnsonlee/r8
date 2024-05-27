// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.conversion.passes.BinopDescriptor.ADD;
import static com.android.tools.r8.ir.conversion.passes.BinopDescriptor.SUB;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.LogicalBinop;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Rem;
import com.android.tools.r8.ir.code.Shl;
import com.android.tools.r8.ir.code.Shr;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Ushr;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class BinopRewriter extends CodeRewriterPass<AppInfo> {

  public BinopRewriter(AppView<?> appView) {
    super(appView);
  }

  private final Map<Class<?>, BinopDescriptor> descriptors = createBinopDescriptors();

  private Map<Class<?>, BinopDescriptor> createBinopDescriptors() {
    ImmutableMap.Builder<Class<?>, BinopDescriptor> builder = ImmutableMap.builder();
    builder.put(Add.class, ADD);
    builder.put(Sub.class, SUB);
    builder.put(Mul.class, BinopDescriptor.MUL);
    builder.put(Div.class, BinopDescriptor.DIV);
    builder.put(Rem.class, BinopDescriptor.REM);
    builder.put(And.class, BinopDescriptor.AND);
    builder.put(Or.class, BinopDescriptor.OR);
    builder.put(Xor.class, BinopDescriptor.XOR);
    builder.put(Shl.class, BinopDescriptor.SHL);
    builder.put(Shr.class, BinopDescriptor.SHR);
    builder.put(Ushr.class, BinopDescriptor.USHR);
    return builder.build();
  }

  @Override
  protected String getRewriterId() {
    return "BinopRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return options.testing.enableBinopOptimization
        && !isDebugMode(code.context())
        && code.metadata().mayHaveArithmeticOrLogicalBinop();
  }

  @Override
  public CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction next = iterator.next();
      if (next.isBinop() && !next.isCmp()) {
        Binop binop = next.asBinop();
        if (binop.getNumericType() == NumericType.INT
            || binop.getNumericType() == NumericType.LONG) {
          BinopDescriptor binopDescriptor = descriptors.get(binop.getClass());
          assert binopDescriptor != null;
          if (identityAbsorbingSimplification(iterator, binop, binopDescriptor, code)) {
            hasChanged = true;
            continue;
          }
          hasChanged |= successiveSimplification(iterator, binop, binopDescriptor, code);
        }
      }
    }
    if (hasChanged) {
      code.removeAllDeadAndTrivialPhis();
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private boolean successiveSimplification(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor, IRCode code) {
    if (binop.outValue().hasDebugUsers()) {
      return false;
    }
    ConstNumber constBLeft = getConstNumber(binop.leftValue());
    ConstNumber constBRight = getConstNumber(binop.rightValue());
    if ((constBLeft != null && constBRight != null)
        || (constBLeft == null && constBRight == null)) {
      return successiveLogicalSimplificationNoConstant(iterator, binop, binopDescriptor, code);
    }
    Value otherValue = constBLeft == null ? binop.leftValue() : binop.rightValue();
    if (otherValue.isPhi() || !otherValue.getDefinition().isBinop()) {
      return false;
    }
    Binop prevBinop = otherValue.getDefinition().asBinop();
    ConstNumber constALeft = getConstNumber(prevBinop.leftValue());
    ConstNumber constARight = getConstNumber(prevBinop.rightValue());
    if ((constALeft != null && constARight != null)
        || (constALeft == null && constARight == null)) {
      return false;
    }
    ConstNumber constB = constBLeft == null ? constBRight : constBLeft;
    ConstNumber constA = constALeft == null ? constARight : constALeft;
    Value input = constALeft == null ? prevBinop.leftValue() : prevBinop.rightValue();
    // We have two successive binops so that a,b constants, x the input and a * x * b.
    if (prevBinop.getClass() == binop.getClass()) {
      if (binopDescriptor.associativeAndCommutative) {
        // a * x * b => x * (a * b) where (a * b) is a constant.
        assert binop.isCommutative();
        rewriteIntoConstThenBinop(
            iterator, binopDescriptor, binopDescriptor, constB, constA, input, true, code);
        return true;
      } else if (binopDescriptor.isShift()) {
        // x shift: a shift: b => x shift: (a + b) where a + b is a constant.
        if (constBRight != null && constARight != null) {
          return rewriteSuccessiveShift(
              iterator, binop, binopDescriptor, constBRight, constARight, input, code);
        }
      } else if (binop.isSub() && constBRight != null) {
        // a - x - b => (a - b) - x where (a - b) is a constant.
        // x - a - b => x - (a + b) where (a + b) is a constant.
        // We ignore b - (x - a) and b - (a - x) with constBRight != null.
        if (constARight == null) {
          rewriteIntoConstThenBinop(iterator, SUB, SUB, constA, constB, input, true, code);
          return true;
        } else {
          rewriteIntoConstThenBinop(iterator, ADD, SUB, constB, constA, input, false, code);
          return true;
        }
      }
    } else {
      if (binop.isSub() && prevBinop.isAdd() && constBRight != null) {
        // x + a - b => x + (a - b) where (a - b) is a constant.
        // a + x - b => x + (a - b) where (a - b) is a constant.
        // We ignore b - (x + a) and b - (a + x) with constBRight != null.
        rewriteIntoConstThenBinop(iterator, SUB, ADD, constA, constB, input, true, code);
        return true;
      } else if (binop.isAdd() && prevBinop.isSub()) {
        // x - a + b => x - (a - b) where (a - b) is a constant.
        // a - x + b => (a + b) - x where (a + b) is a constant.
        if (constALeft == null) {
          rewriteIntoConstThenBinop(iterator, SUB, SUB, constA, constB, input, false, code);
          return true;
        } else {
          rewriteIntoConstThenBinop(iterator, ADD, SUB, constB, constA, input, true, code);
          return true;
        }
      }
    }
    return false;
  }

  private boolean rewriteSuccessiveShift(
      InstructionListIterator iterator,
      Binop binop,
      BinopDescriptor binopDescriptor,
      ConstNumber constBRight,
      ConstNumber constARight,
      Value input,
      IRCode code) {
    assert binop.isShl() || binop.isShr() || binop.isUshr();
    int mask = input.outType().isWide() ? 63 : 31;
    int intA = constARight.getIntValue() & mask;
    int intB = constBRight.getIntValue() & mask;
    if (intA + intB > mask) {
      if (binop.isShr()) {
        return false;
      }
      ConstNumber zero = code.createNumberConstant(0, binop.outValue().getType());
      iterator.replaceCurrentInstruction(zero);
      return true;
    }
    iterator.previous();
    Value newConstantValue =
        iterator.insertConstNumberInstruction(
            code, appView.options(), intA + intB, TypeElement.getInt());
    iterator.next();
    replaceBinop(iterator, code, input, newConstantValue, binopDescriptor);
    return true;
  }

  private boolean successiveLogicalSimplificationNoConstant(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor, IRCode code) {
    if (!(binop.isAnd() || binop.isOr())) {
      return false;
    }
    if (binop.leftValue().isPhi() || binop.rightValue().isPhi()) {
      return false;
    }
    LogicalBinop leftDef = binop.leftValue().getDefinition().asLogicalBinop();
    LogicalBinop rightDef = binop.rightValue().getDefinition().asLogicalBinop();
    if (leftDef == null
        || rightDef == null
        || (leftDef.getClass() != rightDef.getClass())
        || (leftDef.getNumericType() != rightDef.getNumericType())) {
      return false;
    }
    // These optimizations were implemented mostly to deal with Compose specific bit patterns.
    if (leftDef.isAnd() || leftDef.isOr()) {
      return andOrOnCommonInputSimplification(
          iterator, binop, leftDef, rightDef, binopDescriptor, code);
    }
    if (leftDef.isShl() || leftDef.isShr() || leftDef.isUshr()) {
      return shiftOnCommonValueSharing(iterator, binop, leftDef, rightDef, binopDescriptor, code);
    }
    return false;
  }

  private boolean andOrOnCommonInputSimplification(
      InstructionListIterator iterator,
      Instruction binop,
      LogicalBinop leftDef,
      LogicalBinop rightDef,
      BinopDescriptor binopDescriptor,
      IRCode code) {
    // For all permutations of & and |, represented by &| and |&.
    // (x &| a) |& (x &| b) => (a |& b) &| x.
    // a |& b will be simplified into a new constant if both constant.
    Value x, a, b;
    if (leftDef.leftValue() == rightDef.leftValue()) {
      x = leftDef.leftValue();
      a = leftDef.rightValue();
      b = rightDef.rightValue();
    } else if (leftDef.leftValue() == rightDef.rightValue()) {
      x = leftDef.leftValue();
      a = leftDef.rightValue();
      b = rightDef.leftValue();
    } else if (leftDef.rightValue() == rightDef.leftValue()) {
      x = leftDef.rightValue();
      a = leftDef.leftValue();
      b = rightDef.rightValue();
    } else if (leftDef.rightValue() == rightDef.rightValue()) {
      x = leftDef.rightValue();
      a = leftDef.leftValue();
      b = rightDef.leftValue();
    } else {
      return false;
    }

    rewriteIntoTwoSuccessiveBinops(
        iterator,
        binop.getPosition(),
        binopDescriptor,
        descriptors.get(leftDef.getClass()),
        a,
        b,
        x,
        code);
    return true;
  }

  private boolean shiftOnCommonValueSharing(
      InstructionListIterator iterator,
      Instruction binop,
      LogicalBinop leftDef,
      LogicalBinop rightDef,
      BinopDescriptor binopDescriptor,
      IRCode code) {
    // For all permutations of & and |, represented by &|, and any shift operation.
    // (x shift: val) &| (y shift: val) => (x &| y) shift: val.
    // x |& y will be simplified into a new constant if both constant.
    ConstNumber constLeft = getConstNumber(leftDef.rightValue());
    if (constLeft != null) {
      // val is a constant.
      ConstNumber constRight = getConstNumber(rightDef.rightValue());
      if (constRight == null) {
        return false;
      }
      if (constRight.getRawValue() != constLeft.getRawValue()) {
        return false;
      }
    } else {
      // val is not constant.
      if (leftDef.rightValue() != rightDef.rightValue()) {
        return false;
      }
    }

    rewriteIntoTwoSuccessiveBinops(
        iterator,
        binop.getPosition(),
        binopDescriptor,
        descriptors.get(leftDef.getClass()),
        leftDef.leftValue(),
        rightDef.leftValue(),
        leftDef.rightValue(),
        code);
    return true;
  }

  private void rewriteIntoTwoSuccessiveBinops(
      InstructionListIterator iterator,
      Position position,
      BinopDescriptor firstBinop,
      BinopDescriptor secondBinop,
      Value firstLeft,
      Value firstRight,
      Value secondRight,
      IRCode code) {
    // This creates something along the lines of:
    // `(firstLeft firstBinop: firstRight) secondBinop: secondOther`.
    ConstNumber constA = getConstNumber(firstLeft);
    if (constA != null) {
      ConstNumber constB = getConstNumber(firstRight);
      if (constB != null) {
        rewriteIntoConstThenBinop(
            iterator, firstBinop, secondBinop, constA, constB, secondRight, true, code);
        return;
      }
    }
    Binop newFirstBinop = instantiateBinop(code, firstLeft, firstRight, firstBinop);
    newFirstBinop.setPosition(position);
    iterator.previous();
    iterator.add(newFirstBinop);
    iterator.next();
    replaceBinop(iterator, code, newFirstBinop.outValue(), secondRight, secondBinop);
    iterator.previous();
  }

  private void rewriteIntoConstThenBinop(
      InstructionListIterator iterator,
      BinopDescriptor firstBinop,
      BinopDescriptor secondBinop,
      ConstNumber firstLeft,
      ConstNumber firstRight,
      Value secondOther,
      boolean newConstFlowsIntoLeft,
      IRCode code) {
    Value firstOutValue = insertNewConstNumber(code, iterator, firstLeft, firstRight, firstBinop);
    replaceBinop(
        iterator,
        code,
        newConstFlowsIntoLeft ? firstOutValue : secondOther,
        newConstFlowsIntoLeft ? secondOther : firstOutValue,
        secondBinop);
  }

  private void replaceBinop(
      InstructionListIterator iterator,
      IRCode code,
      Value left,
      Value right,
      BinopDescriptor binopDescriptor) {
    Binop newBinop = instantiateBinop(code, left, right, binopDescriptor);
    iterator.replaceCurrentInstruction(newBinop);
    // We need to reset the iterator state to process the new instruction(s).
    iterator.previous();
  }

  private Binop instantiateBinop(IRCode code, Value left, Value right, BinopDescriptor descriptor) {
    TypeElement representative = left.getType().isInt() ? right.getType() : left.getType();
    Value newValue = code.createValue(representative);
    NumericType numericType = representative.isInt() ? NumericType.INT : NumericType.LONG;
    return descriptor.instantiate(numericType, newValue, left, right);
  }

  private Value insertNewConstNumber(
      IRCode code,
      InstructionListIterator iterator,
      ConstNumber left,
      ConstNumber right,
      BinopDescriptor descriptor) {
    TypeElement representative =
        left.outValue().getType().isInt() ? right.outValue().getType() : left.outValue().getType();
    long result =
        representative.isInt()
            ? descriptor.evaluate(left.getIntValue(), right.getIntValue())
            : descriptor.evaluate(left.getLongValue(), right.getLongValue());
    iterator.previous();
    Value value =
        iterator.insertConstNumberInstruction(
            code, appView.options(), result, left.outValue().getType());
    iterator.next();
    return value;
  }

  private boolean identityAbsorbingSimplification(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor, IRCode code) {
    ConstNumber constNumber = getConstNumber(binop.leftValue());
    if (constNumber != null) {
      boolean isBooleanValue = binop.outValue().knownToBeBoolean();
      if (simplify(
          binop,
          iterator,
          constNumber,
          binopDescriptor.leftIdentity(isBooleanValue),
          binop.rightValue(),
          binopDescriptor.leftAbsorbing(isBooleanValue),
          binop.leftValue())) {
        return true;
      }
    }
    constNumber = getConstNumber(binop.rightValue());
    if (constNumber != null) {
      boolean isBooleanValue = binop.outValue().knownToBeBoolean();
      if (simplify(
          binop,
          iterator,
          constNumber,
          binopDescriptor.rightIdentity(isBooleanValue),
          binop.leftValue(),
          binopDescriptor.rightAbsorbing(isBooleanValue),
          binop.rightValue())) {
        return true;
      }
    }
    if (binop.leftValue() == binop.rightValue()) {
      if (binop.isXor() || binop.isSub()) {
        // a ^ a => 0, a - a => 0
        ConstNumber zero = code.createNumberConstant(0, binop.outValue().getType());
        iterator.replaceCurrentInstruction(zero);
      } else if (binop.isAnd() || binop.isOr()) {
        // a & a => a, a | a => a.
        binop.outValue().replaceUsers(binop.leftValue());
        iterator.remove();
      }
      return true;
    }
    return false;
  }

  @SuppressWarnings("ReferenceEquality")
  private ConstNumber getConstNumber(Value val) {
    ConstNumber constNumber = getConstNumberIfConstant(val);
    if (constNumber != null) {
      return constNumber;
    }
    // phi(v1(0), v2(0)) is equivalent to ConstNumber(0) for the simplification.
    if (val.isPhi() && getConstNumberIfConstant(val.asPhi().getOperands().get(0)) != null) {
      ConstNumber phiConstNumber = null;
      WorkList<Phi> phiWorkList = WorkList.newIdentityWorkList(val.asPhi());
      while (phiWorkList.hasNext()) {
        Phi next = phiWorkList.next();
        for (Value operand : next.getOperands()) {
          ConstNumber operandConstNumber = getConstNumberIfConstant(operand);
          if (operandConstNumber != null) {
            if (phiConstNumber == null) {
              phiConstNumber = operandConstNumber;
            } else if (operandConstNumber.getRawValue() == phiConstNumber.getRawValue()) {
              assert operandConstNumber.getOutType() == phiConstNumber.getOutType();
            } else {
              // Different const numbers, cannot conclude a value from the phi.
              return null;
            }
          } else if (operand.isPhi()) {
            phiWorkList.addIfNotSeen(operand.asPhi());
          } else {
            return null;
          }
        }
      }
      return phiConstNumber;
    }
    return null;
  }

  private static ConstNumber getConstNumberIfConstant(Value val) {
    if (val.isConstant() && val.getConstInstruction().isConstNumber()) {
      return val.getConstInstruction().asConstNumber();
    }
    return null;
  }

  private boolean simplify(
      Binop binop,
      InstructionListIterator iterator,
      ConstNumber constNumber,
      Integer identityElement,
      Value identityReplacement,
      Integer absorbingElement,
      Value absorbingReplacement) {
    int intValue;
    if (constNumber.outValue().getType().isInt()) {
      intValue = constNumber.getIntValue();
    } else {
      assert constNumber.outValue().getType().isLong();
      long longValue = constNumber.getLongValue();
      intValue = (int) longValue;
      if ((long) intValue != longValue) {
        return false;
      }
    }
    if (identityElement != null && identityElement == intValue) {
      binop.outValue().replaceUsers(identityReplacement);
      iterator.remove();
      return true;
    }
    if (absorbingElement != null && absorbingElement == intValue) {
      binop.outValue().replaceUsers(absorbingReplacement);
      iterator.remove();
      return true;
    }
    return false;
  }
}
