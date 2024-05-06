// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;

class FlowGraphParameterNode extends FlowGraphNode {

  private final ProgramMethod method;
  private final MethodState methodState;
  private final int parameterIndex;
  private final DexType parameterType;

  FlowGraphParameterNode(
      ProgramMethod method, MethodState methodState, int parameterIndex, DexType parameterType) {
    assert methodState.isMonomorphic() || methodState.isUnknown();
    this.method = method;
    this.methodState = methodState;
    this.parameterIndex = parameterIndex;
    this.parameterType = parameterType;
  }

  ProgramMethod getMethod() {
    return method;
  }

  int getParameterIndex() {
    return parameterIndex;
  }

  @Override
  DexType getStaticType() {
    return parameterType;
  }

  @Override
  ValueState getState() {
    return methodState.isMonomorphic()
        ? methodState.asMonomorphic().getParameterState(parameterIndex)
        : ValueState.unknown();
  }

  @Override
  void setState(ValueState parameterState) {
    if (methodState.isMonomorphic()) {
      methodState.asMonomorphic().setParameterState(parameterIndex, parameterState);
    } else {
      assert parameterState.isUnknown();
    }
  }

  @Override
  boolean isParameterNode() {
    return true;
  }

  @Override
  FlowGraphParameterNode asParameterNode() {
    return this;
  }

  @Override
  boolean isReceiverNode() {
    return !method.getAccessFlags().isStatic() && parameterIndex == 0;
  }
}
