// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** A shared base class for non-trivial field information (neither bottom nor top). */
public abstract class ConcreteFieldState extends NonEmptyFieldState {

  private AbstractValue abstractValue;
  private Set<InFlow> inFlow;

  ConcreteFieldState(AbstractValue abstractValue, Set<InFlow> inFlow) {
    this.abstractValue = abstractValue;
    this.inFlow = inFlow;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return abstractValue;
  }

  public boolean hasInFlow() {
    return !inFlow.isEmpty();
  }

  public Set<InFlow> getInFlow() {
    assert inFlow.isEmpty() || inFlow instanceof HashSet<?>;
    return inFlow;
  }

  public FieldState clearInFlow() {
    if (hasInFlow()) {
      inFlow = Collections.emptySet();
      if (isEffectivelyBottom()) {
        return bottom();
      }
    }
    assert !isEffectivelyBottom();
    return this;
  }

  public Set<InFlow> copyInFlow() {
    if (inFlow.isEmpty()) {
      assert inFlow == Collections.<InFlow>emptySet();
      return inFlow;
    }
    return new HashSet<>(inFlow);
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcreteFieldState asConcrete() {
    return this;
  }

  public boolean isEffectivelyBottom() {
    return abstractValue.isBottom() && !hasInFlow();
  }

  public boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown();
  }

  @Override
  public final FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      NonEmptyFieldState fieldState,
      Action onChangedAction) {
    if (fieldState.isUnknown()) {
      return fieldState;
    }
    ConcreteFieldState concreteFieldState = fieldState.asConcrete();
    if (isReference()) {
      assert concreteFieldState.isReference();
      return asReference()
          .mutableJoin(appView, field, concreteFieldState.asReference(), onChangedAction);
    }
    return asPrimitive()
        .mutableJoin(appView, field, concreteFieldState.asPrimitive(), onChangedAction);
  }

  @Override
  public FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      NonEmptyParameterState parameterState,
      Action onChangedAction) {
    if (parameterState.isUnknown()) {
      return unknown();
    }
    ConcreteParameterState concreteParameterState = parameterState.asConcrete();
    if (isReference()) {
      assert concreteParameterState.isReferenceParameter();
      return asReference()
          .mutableJoin(
              appView, field, concreteParameterState.asReferenceParameter(), onChangedAction);
    }
    return asPrimitive()
        .mutableJoin(
            appView, field, concreteParameterState.asPrimitiveParameter(), onChangedAction);
  }

  @Override
  public final FieldState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      Function<FieldState, NonEmptyFieldState> fieldStateSupplier) {
    return mutableJoin(appView, field, fieldStateSupplier.apply(this));
  }

  final boolean mutableJoinAbstractValue(
      AppView<AppInfoWithLiveness> appView, ProgramField field, ConcreteFieldState fieldState) {
    return mutableJoinAbstractValue(appView, field, fieldState.abstractValue);
  }

  final boolean mutableJoinAbstractValue(
      AppView<AppInfoWithLiveness> appView, ProgramField field, AbstractValue otherAbstractValue) {
    AbstractValue oldAbstractValue = abstractValue;
    abstractValue =
        appView.getAbstractValueFieldJoiner().join(abstractValue, otherAbstractValue, field);
    return !abstractValue.equals(oldAbstractValue);
  }

  final boolean mutableJoinInFlow(ConcreteFieldState fieldState) {
    return mutableJoinInFlow(fieldState.inFlow);
  }

  final boolean mutableJoinInFlow(Set<InFlow> otherInFlow) {
    if (otherInFlow.isEmpty()) {
      return false;
    }
    if (inFlow.isEmpty()) {
      assert inFlow == Collections.<InFlow>emptySet();
      inFlow = new HashSet<>();
    }
    return inFlow.addAll(otherInFlow);
  }

  /**
   * Returns true if the in-parameters set should be widened to unknown, in which case the entire
   * parameter state must be widened to unknown.
   */
  boolean widenInFlow(AppView<AppInfoWithLiveness> appView) {
    return inFlow != null
        && inFlow.size() > appView.options().callSiteOptimizationOptions().getMaxInFlowSize();
  }
}
