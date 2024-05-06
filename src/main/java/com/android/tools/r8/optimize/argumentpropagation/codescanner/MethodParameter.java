// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.Objects;

public class MethodParameter implements BaseInFlow {

  private final DexMethod method;
  private final int index;
  private final boolean isMethodStatic;

  public MethodParameter(DexClassAndMethod method, int index) {
    this(method.getReference(), index, method.getAccessFlags().isStatic());
  }

  public MethodParameter(DexMethod method, int index, boolean isMethodStatic) {
    this.method = method;
    this.index = index;
    this.isMethodStatic = isMethodStatic;
  }

  public static MethodParameter createStatic(DexMethod method, int index) {
    return new MethodParameter(method, index, true);
  }

  public DexMethod getMethod() {
    return method;
  }

  public int getIndex() {
    return index;
  }

  public DexType getType() {
    return method.getArgumentType(index, isMethodStatic);
  }

  @Override
  public boolean isMethodParameter() {
    return true;
  }

  @Override
  public MethodParameter asMethodParameter() {
    return this;
  }

  @Override
  @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MethodParameter methodParameter = (MethodParameter) obj;
    return method == methodParameter.method && index == methodParameter.index;
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, index);
  }

  @Override
  public String toString() {
    return "MethodParameter(" + method + ", " + index + ")";
  }
}
