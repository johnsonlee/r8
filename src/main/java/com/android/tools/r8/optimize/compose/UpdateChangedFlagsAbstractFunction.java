// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.analysis.value.arithmetic.AbstractCalculator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlowKind;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.OrAbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IterableUtils;
import java.util.Objects;

public class UpdateChangedFlagsAbstractFunction implements AbstractFunction {

  private static final int changedLowBitMask = 0b001_001_001_001_001_001_001_001_001_001_0;
  private static final int changedHighBitMask = changedLowBitMask << 1;
  private static final int changedMask = ~(changedLowBitMask | changedHighBitMask);

  private final InFlow inFlow;

  public UpdateChangedFlagsAbstractFunction(InFlow inFlow) {
    this.inFlow = inFlow;
  }

  @Override
  public ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState baseInState) {
    ValueState inState;
    if (inFlow.isAbstractFunction()) {
      AbstractFunction orFunction = inFlow.asAbstractFunction();
      assert orFunction instanceof OrAbstractFunction;
      inState = orFunction.apply(appView, flowGraphStateProvider, baseInState);
    } else {
      inState = baseInState;
    }
    if (!inState.isPrimitiveState()) {
      assert inState.isBottom() || inState.isUnknown();
      return inState;
    }
    AbstractValue result = apply(appView, inState.asPrimitiveState().getAbstractValue());
    return ConcretePrimitiveTypeValueState.create(result);
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
  private AbstractValue apply(AppView<AppInfoWithLiveness> appView, AbstractValue flagsValue) {
    if (flagsValue.isSingleNumberValue()) {
      return apply(appView, flagsValue.asSingleNumberValue().getIntValue());
    }
    AbstractValueFactory factory = appView.abstractValueFactory();
    // Load constants.
    AbstractValue changedLowBitMaskValue =
        factory.createUncheckedSingleNumberValue(changedLowBitMask);
    AbstractValue changedHighBitMaskValue =
        factory.createUncheckedSingleNumberValue(changedHighBitMask);
    AbstractValue changedMaskValue = factory.createUncheckedSingleNumberValue(changedMask);
    // Evaluate expression.
    AbstractValue lowBitsValue =
        AbstractCalculator.andIntegers(appView, flagsValue, changedLowBitMaskValue);
    AbstractValue highBitsValue =
        AbstractCalculator.andIntegers(appView, flagsValue, changedHighBitMaskValue);
    AbstractValue changedBitsValue =
        AbstractCalculator.andIntegers(appView, flagsValue, changedMaskValue);
    return AbstractCalculator.orIntegers(
        appView,
        changedBitsValue,
        lowBitsValue,
        AbstractCalculator.shrIntegers(appView, highBitsValue, 1),
        AbstractCalculator.andIntegers(
            appView, AbstractCalculator.shlIntegers(appView, lowBitsValue, 1), highBitsValue));
  }

  private SingleNumberValue apply(AppView<AppInfoWithLiveness> appView, int flags) {
    int lowBits = flags & changedLowBitMask;
    int highBits = flags & changedHighBitMask;
    int changedBits = flags & changedMask;
    int result = changedBits | lowBits | (highBits >> 1) | ((lowBits << 1) & highBits);
    return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
  }

  @Override
  public boolean verifyContainsBaseInFlow(BaseInFlow otherInFlow) {
    if (inFlow.isAbstractFunction()) {
      assert inFlow.asAbstractFunction().verifyContainsBaseInFlow(otherInFlow);
    } else {
      assert inFlow.isBaseInFlow();
      assert inFlow.equals(otherInFlow);
    }
    return true;
  }

  @Override
  public Iterable<BaseInFlow> getBaseInFlow() {
    if (inFlow.isAbstractFunction()) {
      return inFlow.asAbstractFunction().getBaseInFlow();
    }
    assert inFlow.isBaseInFlow();
    return IterableUtils.singleton(inFlow.asBaseInFlow());
  }

  @Override
  public InFlowKind getKind() {
    return InFlowKind.ABSTRACT_FUNCTION_UPDATE_CHANGED_FLAGS;
  }

  @Override
  public int internalCompareToSameKind(InFlow other) {
    return inFlow.compareTo(other.asUpdateChangedFlagsAbstractFunction().inFlow);
  }

  @Override
  public boolean isUpdateChangedFlagsAbstractFunction() {
    return true;
  }

  @Override
  public UpdateChangedFlagsAbstractFunction asUpdateChangedFlagsAbstractFunction() {
    return this;
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
    UpdateChangedFlagsAbstractFunction fn = (UpdateChangedFlagsAbstractFunction) obj;
    return inFlow.equals(fn.inFlow);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), inFlow);
  }
}
