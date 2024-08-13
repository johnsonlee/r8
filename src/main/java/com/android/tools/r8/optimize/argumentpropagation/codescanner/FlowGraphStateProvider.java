// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.optimize.argumentpropagation.propagation.FlowGraph;
import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Supplier;

public interface FlowGraphStateProvider {

  static FlowGraphStateProvider create(FlowGraph flowGraph, AbstractFunction abstractFunction) {
    if (!InternalOptions.assertionsEnabled()) {
      return flowGraph;
    }
    // If the abstract function is a canonical function, or the abstract function has a single
    // declared input, we should never perform any state lookups.
    if (abstractFunction.hasSingleInFlow()) {
      assert abstractFunction.isIdentity()
          || abstractFunction.isCastAbstractFunction()
          || abstractFunction.isUnknownAbstractFunction()
          || abstractFunction.isUpdateChangedFlagsAbstractFunction();
      return new FlowGraphStateProvider() {

        @Override
        public ValueState getState(DexField field) {
          throw new Unreachable();
        }

        @Override
        public ValueState getState(BaseInFlow inFlow, Supplier<ValueState> defaultStateProvider) {
          throw new Unreachable();
        }
      };
    }
    // Otherwise, restrict state lookups to the declared base in flow. This is required for arriving
    // at the correct fix point.
    assert abstractFunction.isInstanceFieldReadAbstractFunction();
    return new FlowGraphStateProvider() {

      @Override
      public ValueState getState(DexField field) {
        assert abstractFunction.verifyContainsBaseInFlow(new FieldValue(field));
        return flowGraph.getState(field);
      }

      @Override
      public ValueState getState(BaseInFlow inFlow, Supplier<ValueState> defaultStateProvider) {
        assert abstractFunction.verifyContainsBaseInFlow(inFlow);
        return flowGraph.getState(inFlow, defaultStateProvider);
      }
    };
  }

  ValueState getState(DexField field);

  ValueState getState(BaseInFlow inFlow, Supplier<ValueState> defaultStateProvider);
}
