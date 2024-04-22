// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;

public class FlowGraphFieldNode extends FlowGraphNode {

  private final ProgramField field;
  private ValueState fieldState;

  FlowGraphFieldNode(ProgramField field, ValueState fieldState) {
    this.field = field;
    this.fieldState = fieldState;
  }

  public ProgramField getField() {
    return field;
  }

  @Override
  DexType getStaticType() {
    return field.getType();
  }

  @Override
  ValueState getState() {
    return fieldState;
  }

  @Override
  void setState(ValueState fieldState) {
    this.fieldState = fieldState;
  }

  @Override
  boolean isFieldNode() {
    return true;
  }

  @Override
  FlowGraphFieldNode asFieldNode() {
    return this;
  }
}
