// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.List;

public class ConcreteMonomorphicMethodState extends ConcreteMethodState
    implements ConcreteMonomorphicMethodStateOrBottom, ConcreteMonomorphicMethodStateOrUnknown {

  boolean isReturnValueUsed;
  List<ValueState> parameterStates;

  public ConcreteMonomorphicMethodState(
      boolean isReturnValueUsed, List<ValueState> parameterStates) {
    assert Streams.stream(Iterables.skip(parameterStates, 1))
        .noneMatch(x -> x.isConcrete() && x.asConcrete().isReceiverState());
    this.isReturnValueUsed = isReturnValueUsed;
    this.parameterStates = parameterStates;
    assert !isEffectivelyUnknown() : "Must use UnknownMethodState instead";
  }

  public static ConcreteMonomorphicMethodStateOrUnknown create(
      boolean isReturnValueUsed, List<ValueState> parameterStates) {
    return isEffectivelyUnknown(isReturnValueUsed, parameterStates)
        ? unknown()
        : new ConcreteMonomorphicMethodState(isReturnValueUsed, parameterStates);
  }

  public ValueState getParameterState(int index) {
    return parameterStates.get(index);
  }

  public List<ValueState> getParameterStates() {
    return parameterStates;
  }

  public boolean isReturnValueUsed() {
    return isReturnValueUsed;
  }

  public boolean isEffectivelyBottom() {
    return Iterables.any(parameterStates, ValueState::isBottom);
  }

  public boolean isEffectivelyUnknown() {
    return isEffectivelyUnknown(isReturnValueUsed, parameterStates);
  }

  private static boolean isEffectivelyUnknown(
      boolean isReturnValueUsed, List<ValueState> parameterStates) {
    return isReturnValueUsed && Iterables.all(parameterStates, ValueState::isUnknown);
  }

  @Override
  public ConcreteMonomorphicMethodState mutableCopy() {
    List<ValueState> copiedParametersStates = new ArrayList<>(size());
    for (ValueState parameterState : getParameterStates()) {
      copiedParametersStates.add(parameterState.mutableCopy());
    }
    return new ConcreteMonomorphicMethodState(isReturnValueUsed, copiedParametersStates);
  }

  public ConcreteMonomorphicMethodStateOrUnknown mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      ConcreteMonomorphicMethodState methodState,
      StateCloner cloner) {
    if (size() != methodState.size()) {
      assert false;
      return unknown();
    }

    if (methodState.isReturnValueUsed()) {
      isReturnValueUsed = true;
    }

    int argumentIndex = 0;
    if (size() > methodSignature.getArity()) {
      assert size() == methodSignature.getArity() + 1;
      ValueState parameterState = parameterStates.get(0);
      ValueState otherParameterState = methodState.parameterStates.get(0);
      DexType inStaticType = null;
      DexType outStaticType = null;
      parameterStates.set(
          0,
          parameterState.mutableJoin(
              appView, otherParameterState, inStaticType, outStaticType, cloner));
      argumentIndex++;
    }

    for (int parameterIndex = 0; argumentIndex < size(); argumentIndex++, parameterIndex++) {
      ValueState parameterState = parameterStates.get(argumentIndex);
      ValueState otherParameterState = methodState.parameterStates.get(argumentIndex);
      DexType inStaticType = null;
      DexType outStaticType = methodSignature.getParameter(parameterIndex);
      parameterStates.set(
          argumentIndex,
          parameterState.mutableJoin(
              appView, otherParameterState, inStaticType, outStaticType, cloner));
      assert !parameterStates.get(argumentIndex).isConcrete()
          || !parameterStates.get(argumentIndex).asConcrete().isReceiverState();
    }

    return isEffectivelyUnknown() ? unknown() : this;
  }

  @Override
  public boolean isMonomorphic() {
    return true;
  }

  @Override
  public ConcreteMonomorphicMethodState asMonomorphic() {
    return this;
  }

  @Override
  public ConcreteMonomorphicMethodStateOrBottom asMonomorphicOrBottom() {
    return this;
  }

  public void setParameterState(int index, ValueState parameterState) {
    assert index == 0
        || !parameterState.isConcrete()
        || !parameterState.asConcrete().isReceiverState();
    parameterStates.set(index, parameterState);
  }

  public int size() {
    return parameterStates.size();
  }
}
