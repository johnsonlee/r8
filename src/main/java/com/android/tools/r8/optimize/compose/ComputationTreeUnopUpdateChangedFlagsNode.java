// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.DefiniteBitsNumberValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.analysis.value.arithmetic.AbstractCalculator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeUnopNode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.Objects;
import java.util.function.Function;

public class ComputationTreeUnopUpdateChangedFlagsNode extends ComputationTreeUnopNode {

  private static final int changedLowBitMask = 0b001_001_001_001_001_001_001_001_001_001_0;
  private static final int changedHighBitMask = changedLowBitMask << 1;
  private static final int changedMask = ~(changedLowBitMask | changedHighBitMask);

  public ComputationTreeUnopUpdateChangedFlagsNode(ComputationTreeNode operand) {
    super(operand);
  }

  public static ComputationTreeNode create(ComputationTreeNode operand) {
    if (operand.isUnknown()) {
      return AbstractValue.unknown();
    }
    return new ComputationTreeUnopUpdateChangedFlagsNode(operand);
  }

  @Override
  public AbstractValue evaluate(
      AppView<AppInfoWithLiveness> appView, FlowGraphStateProvider flowGraphStateProvider) {
    AbstractValue operandValue = operand.evaluate(appView, flowGraphStateProvider);
    if (operandValue.isBottom()) {
      return operandValue;
    } else if (operandValue.isSingleNumberValue()) {
      return evaluateConcrete(appView, operandValue.asSingleNumberValue().getIntValue());
    } else if (operandValue.isDefiniteBitsNumberValue()) {
      return evaluateAbstract(appView, operandValue.asDefiniteBitsNumberValue());
    } else {
      assert !operandValue.hasDefinitelySetAndUnsetBitsInformation();
      return AbstractValue.unknown();
    }
  }

  /**
   * Applies the following function to the given {@param abstractValue}.
   *
   * <pre>
   * private const val changedLowBitMask = 0b001_001_001_001_001_001_001_001_001_001_0
   * private const val changedHighBitMask = changedLowBitMask shl 1
   * private const val changedMask = (changedLowBitMask or changedHighBitMask).inv()
   *
   * internal fun updateChangedFlags(flags: Int): Int {
   *     val lowBits = flags and changedLowBitMask
   *     val highBits = flags and changedHighBitMask
   *     return ((flags and changedMask) or
   *         (lowBits or (highBits shr 1)) or ((lowBits shl 1) and highBits))
   * }
   * </pre>
   */
  private AbstractValue evaluateAbstract(
      AppView<AppInfoWithLiveness> appView, DefiniteBitsNumberValue flags) {
    AbstractValueFactory factory = appView.abstractValueFactory();
    // Load constants.
    AbstractValue changedLowBitMaskValue =
        factory.createUncheckedSingleNumberValue(changedLowBitMask);
    AbstractValue changedHighBitMaskValue =
        factory.createUncheckedSingleNumberValue(changedHighBitMask);
    AbstractValue changedMaskValue = factory.createUncheckedSingleNumberValue(changedMask);
    // Evaluate expression.
    AbstractValue lowBitsValue =
        AbstractCalculator.andIntegers(appView, flags, changedLowBitMaskValue);
    AbstractValue highBitsValue =
        AbstractCalculator.andIntegers(appView, flags, changedHighBitMaskValue);
    AbstractValue changedBitsValue =
        AbstractCalculator.andIntegers(appView, flags, changedMaskValue);
    return AbstractCalculator.orIntegers(
        appView,
        changedBitsValue,
        lowBitsValue,
        AbstractCalculator.shrIntegers(appView, highBitsValue, 1),
        AbstractCalculator.andIntegers(
            appView, AbstractCalculator.shlIntegers(appView, lowBitsValue, 1), highBitsValue));
  }

  private SingleNumberValue evaluateConcrete(AppView<AppInfoWithLiveness> appView, int flags) {
    int lowBits = flags & changedLowBitMask;
    int highBits = flags & changedHighBitMask;
    int changedBits = flags & changedMask;
    int result = changedBits | lowBits | (highBits >> 1) | ((lowBits << 1) & highBits);
    return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
  }

  @Override
  public boolean isUpdateChangedFlags() {
    return true;
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    return operand.traverseBaseInFlow(fn);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ComputationTreeUnopUpdateChangedFlagsNode fn = (ComputationTreeUnopUpdateChangedFlagsNode) obj;
    return operand.equals(fn.operand);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), operand);
  }

  @Override
  public String toString() {
    return "UpdateChangedFlags(" + operand + ")";
  }
}
