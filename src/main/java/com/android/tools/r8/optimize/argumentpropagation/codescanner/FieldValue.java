// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.function.Function;

// TODO(b/296030319): Change DexField to implement InFlow and use DexField in all places instead of
//  FieldValue to avoid wrappers? This would also remove the need for the FieldValueFactory.
public class FieldValue implements BaseInFlow, ComputationTreeNode {

  private final DexField field;

  public FieldValue(DexField field) {
    this.field = field;
  }

  @Override
  public AbstractValue evaluate(
      AppView<AppInfoWithLiveness> appView, FlowGraphStateProvider flowGraphStateProvider) {
    return flowGraphStateProvider.getState(field).getAbstractValue(appView);
  }

  public DexField getField() {
    return field;
  }

  @Override
  public InFlowKind getKind() {
    return InFlowKind.FIELD;
  }

  @Override
  public BaseInFlow getSingleOpenVariable() {
    return this;
  }

  @Override
  public int internalCompareToSameKind(InFlow other, InFlowComparator comparator) {
    return field.compareTo(other.asFieldValue().getField());
  }

  @Override
  public boolean isComputationLeaf() {
    return true;
  }

  @Override
  public boolean isFieldValue() {
    return true;
  }

  @Override
  public boolean isFieldValue(DexField field) {
    return this.field.isIdenticalTo(field);
  }

  @Override
  public FieldValue asFieldValue() {
    return this;
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    return fn.apply(this);
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
    FieldValue fieldValue = (FieldValue) obj;
    return field.isIdenticalTo(fieldValue.field);
  }

  @Override
  public int hashCode() {
    return field.hashCode();
  }

  @Override
  public String toString() {
    return field.toSourceString();
  }
}
