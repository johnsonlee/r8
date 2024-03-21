// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class ConcreteValueState extends NonEmptyValueState {

  public enum ConcreteParameterStateKind {
    ARRAY,
    CLASS,
    PRIMITIVE,
    RECEIVER
  }

  private Set<InFlow> inFlow;

  ConcreteValueState(Set<InFlow> inFlow) {
    this.inFlow = inFlow;
  }

  public static ConcreteValueState create(DexType staticType, InFlow inFlow) {
    if (staticType.isArrayType()) {
      return new ConcreteArrayTypeValueState(inFlow);
    } else if (staticType.isClassType()) {
      return new ConcreteClassTypeValueState(inFlow);
    } else {
      assert staticType.isPrimitiveType();
      return new ConcretePrimitiveTypeValueState(inFlow);
    }
  }

  public abstract ValueState clearInFlow();

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

  public abstract boolean isEffectivelyBottom();

  public abstract boolean isEffectivelyUnknown();

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcreteValueState asConcrete() {
    return this;
  }

  @Override
  public final NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ValueState state,
      DexType staticType,
      StateCloner cloner,
      Action onChangedAction) {
    if (state.isBottom()) {
      return this;
    }
    if (state.isUnknown()) {
      return unknown();
    }
    ConcreteValueState concreteState = state.asConcrete();
    if (isReferenceState()) {
      assert concreteState.isReferenceState();
      return asReferenceState()
          .mutableJoin(appView, concreteState.asReferenceState(), staticType, onChangedAction);
    }
    return asPrimitiveState()
        .mutableJoin(appView, concreteState.asPrimitiveState(), staticType, onChangedAction);
  }

  boolean mutableJoinInFlow(ConcreteValueState state) {
    return mutableJoinInFlow(state.getInFlow());
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
