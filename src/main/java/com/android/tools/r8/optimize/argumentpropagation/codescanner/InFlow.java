// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.function.Function;

public interface InFlow {

  default int compareTo(InFlow inFlow, InFlowComparator comparator) {
    if (getKind() == inFlow.getKind()) {
      return internalCompareToSameKind(inFlow, comparator);
    }
    return getKind().ordinal() - inFlow.getKind().ordinal();
  }

  int internalCompareToSameKind(InFlow inFlow, InFlowComparator comparator);

  InFlowKind getKind();

  default boolean isAbstractComputation() {
    return false;
  }

  default ComputationTreeNode asAbstractComputation() {
    return null;
  }

  default boolean isAbstractFunction() {
    return false;
  }

  default AbstractFunction asAbstractFunction() {
    return null;
  }

  default boolean isBaseInFlow() {
    return false;
  }

  default BaseInFlow asBaseInFlow() {
    return null;
  }

  default boolean isCastAbstractFunction() {
    return false;
  }

  default CastAbstractFunction asCastAbstractFunction() {
    return null;
  }

  default boolean isFieldValue() {
    return false;
  }

  default boolean isFieldValue(DexField field) {
    return false;
  }

  default FieldValue asFieldValue() {
    return null;
  }

  default boolean isIfThenElseAbstractFunction() {
    return false;
  }

  default IfThenElseAbstractFunction asIfThenElseAbstractFunction() {
    return null;
  }

  default boolean isInstanceFieldReadAbstractFunction() {
    return false;
  }

  default InstanceFieldReadAbstractFunction asInstanceFieldReadAbstractFunction() {
    return null;
  }

  default boolean isMethodParameter() {
    return false;
  }

  default boolean isMethodParameter(DexMethod method, int parameterIndex) {
    return false;
  }

  default MethodParameter asMethodParameter() {
    return null;
  }

  default boolean isUnknown() {
    return false;
  }

  <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn);
}
