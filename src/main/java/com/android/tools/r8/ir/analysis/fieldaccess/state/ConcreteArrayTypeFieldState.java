// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteReferenceTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

/**
 * The information that we track for fields with an array type.
 *
 * <p>Since we don't gain much from tracking the dynamic types of arrays, this is only tracking the
 * abstract value.
 */
public class ConcreteArrayTypeFieldState extends ConcreteReferenceTypeFieldState {

  public ConcreteArrayTypeFieldState(InFlow inFlow) {
    this(SetUtils.newHashSet(inFlow));
  }

  private ConcreteArrayTypeFieldState(Set<InFlow> inFlow) {
    this(AbstractValue.bottom(), inFlow);
  }

  @SuppressWarnings("InconsistentOverloads")
  private ConcreteArrayTypeFieldState(AbstractValue abstractValue, Set<InFlow> inFlow) {
    super(abstractValue, inFlow);
  }

  public static NonEmptyFieldState create(AbstractValue abstractValue) {
    return create(abstractValue, Collections.emptySet());
  }

  public static NonEmptyFieldState create(AbstractValue abstractValue, Set<InFlow> inFlow) {
    return abstractValue.isUnknown()
        ? FieldState.unknown()
        : new ConcreteArrayTypeFieldState(abstractValue, inFlow);
  }

  @Override
  public boolean isArray() {
    return true;
  }

  @Override
  public ConcreteArrayTypeFieldState asArray() {
    return this;
  }

  @Override
  public FieldState mutableCopy() {
    return new ConcreteArrayTypeFieldState(getAbstractValue(), copyInFlow());
  }

  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ProgramField field, AbstractValue abstractValue) {
    mutableJoinAbstractValue(appView, field, abstractValue);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    return this;
  }

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcreteReferenceTypeFieldState fieldState,
      Action onChangedAction) {
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

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      ConcreteReferenceTypeParameterState parameterState,
      Action onChangedAction) {
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, field, parameterState.getAbstractValue(appView));
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
