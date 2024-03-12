// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

/** The information that we track for fields whose type is a primitive type. */
public class ConcretePrimitiveTypeFieldState extends ConcreteFieldState {

  public ConcretePrimitiveTypeFieldState(InFlow inFlow) {
    this(SetUtils.newHashSet(inFlow));
  }

  private ConcretePrimitiveTypeFieldState(Set<InFlow> inFlow) {
    this(AbstractValue.bottom(), inFlow);
  }

  @SuppressWarnings("InconsistentOverloads")
  private ConcretePrimitiveTypeFieldState(AbstractValue abstractValue, Set<InFlow> inFlow) {
    super(abstractValue, inFlow);
  }

  public static NonEmptyFieldState create(AbstractValue abstractValue) {
    return create(abstractValue, Collections.emptySet());
  }

  public static NonEmptyFieldState create(AbstractValue abstractValue, Set<InFlow> inFlow) {
    return abstractValue.isUnknown()
        ? FieldState.unknown()
        : new ConcretePrimitiveTypeFieldState(abstractValue, inFlow);
  }

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public ConcretePrimitiveTypeFieldState asPrimitive() {
    return this;
  }

  @Override
  public FieldState mutableCopy() {
    return new ConcretePrimitiveTypeFieldState(getAbstractValue(), copyInFlow());
  }

  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ProgramField field, AbstractValue abstractValue) {
    mutableJoinAbstractValue(appView, field, abstractValue);
    if (isEffectivelyUnknown()) {
      return FieldState.unknown();
    }
    return this;
  }

  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcretePrimitiveTypeFieldState fieldState,
      Action onChangedAction) {
    assert field.getType().isPrimitiveType();
    boolean abstractValueChanged = mutableJoinAbstractValue(appView, field, fieldState);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(fieldState);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (abstractValueChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcretePrimitiveTypeParameterState parameterState,
      Action onChangedAction) {
    assert field.getType().isPrimitiveType();
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, field, parameterState.getAbstractValue());
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(parameterState.getInFlow());
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (abstractValueChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }
}
