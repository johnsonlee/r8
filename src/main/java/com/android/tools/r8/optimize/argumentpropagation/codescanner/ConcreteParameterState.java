// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcreteFieldState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class ConcreteParameterState extends NonEmptyParameterState {

  public enum ConcreteParameterStateKind {
    ARRAY,
    CLASS,
    PRIMITIVE,
    RECEIVER
  }

  private Set<InFlow> inFlow;

  ConcreteParameterState(Set<InFlow> inFlow) {
    this.inFlow = inFlow;
  }

  public abstract ParameterState clearInFlow();

  void internalClearInFlow() {
    inFlow = Collections.emptySet();
  }

  public Set<InFlow> copyInFlow() {
    if (inFlow.isEmpty()) {
      assert inFlow == Collections.<InFlow>emptySet();
      return inFlow;
    }
    return new HashSet<>(inFlow);
  }

  public boolean hasInFlow() {
    return !inFlow.isEmpty();
  }

  public Set<InFlow> getInFlow() {
    assert inFlow.isEmpty() || inFlow instanceof HashSet<?>;
    return inFlow;
  }

  public abstract ConcreteParameterStateKind getKind();

  public boolean isArrayParameter() {
    return false;
  }

  public ConcreteArrayTypeParameterState asArrayParameter() {
    return null;
  }

  public boolean isClassParameter() {
    return false;
  }

  public ConcreteClassTypeParameterState asClassParameter() {
    return null;
  }

  public abstract boolean isEffectivelyBottom();

  public abstract boolean isEffectivelyUnknown();

  public boolean isPrimitiveParameter() {
    return false;
  }

  public ConcretePrimitiveTypeParameterState asPrimitiveParameter() {
    return null;
  }

  public boolean isReceiverParameter() {
    return false;
  }

  public ConcreteReceiverParameterState asReceiverParameter() {
    return null;
  }

  public boolean isReferenceParameter() {
    return false;
  }

  public ConcreteReferenceTypeParameterState asReferenceParameter() {
    return null;
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcreteParameterState asConcrete() {
    return this;
  }

  @Override
  public final ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ParameterState parameterState,
      DexType parameterType,
      StateCloner cloner,
      Action onChangedAction) {
    if (parameterState.isBottom()) {
      return this;
    }
    if (parameterState.isUnknown()) {
      return parameterState;
    }
    ConcreteParameterState concreteParameterState = parameterState.asConcrete();
    if (isReferenceParameter()) {
      assert concreteParameterState.isReferenceParameter();
      return asReferenceParameter()
          .mutableJoin(
              appView,
              concreteParameterState.asReferenceParameter(),
              parameterType,
              onChangedAction);
    }
    return asPrimitiveParameter()
        .mutableJoin(
            appView, concreteParameterState.asPrimitiveParameter(), parameterType, onChangedAction);
  }

  @Override
  public final ParameterState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcreteFieldState fieldState,
      DexType parameterType,
      Action onChangedAction) {
    if (isReferenceParameter()) {
      assert fieldState.isReference();
      return asReferenceParameter()
          .mutableJoin(appView, fieldState.asReference(), parameterType, onChangedAction);
    }
    return asPrimitiveParameter()
        .mutableJoin(appView, fieldState.asPrimitive(), parameterType, onChangedAction);
  }

  boolean mutableJoinInFlow(ConcreteParameterState parameterState) {
    return mutableJoinInFlow(parameterState.getInFlow());
  }

  boolean mutableJoinInFlow(Set<InFlow> otherInFlow) {
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
