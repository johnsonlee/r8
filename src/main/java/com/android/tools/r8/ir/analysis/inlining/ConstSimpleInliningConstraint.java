// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ConstSimpleInliningConstraint extends SimpleInliningArgumentConstraint {

  private ConstSimpleInliningConstraint(int argumentIndex) {
    super(argumentIndex);
  }

  static ConstSimpleInliningConstraint create(
      int argumentIndex, SimpleInliningConstraintFactory witness) {
    assert witness != null;
    return new ConstSimpleInliningConstraint(argumentIndex);
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    Value argumentRoot = invoke.getArgument(getArgumentIndex()).getAliasedValue();
    return argumentRoot.isDefinedByInstructionSatisfying(Instruction::isConstInstruction);
  }

  @Override
  public SimpleInliningConstraint fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView,
      ArgumentInfoCollection changes,
      SimpleInliningConstraintFactory factory) {
    if (changes.isArgumentRemoved(getArgumentIndex())) {
      RemovedArgumentInfo removedArgumentInfo =
          changes.getArgumentInfo(getArgumentIndex()).asRemovedArgumentInfo();
      if (!removedArgumentInfo.hasSingleValue()) {
        // We should never have constraints for unused arguments.
        assert false;
        return NeverSimpleInliningConstraint.getInstance();
      }
      SingleValue singleValue = removedArgumentInfo.getSingleValue();
      return singleValue.isSingleConstValue()
          ? AlwaysSimpleInliningConstraint.getInstance()
          : NeverSimpleInliningConstraint.getInstance();
    } else {
      assert !changes.hasArgumentInfo(getArgumentIndex());
    }
    return withArgumentIndex(changes.getNewArgumentIndex(getArgumentIndex()), factory);
  }

  @Override
  SimpleInliningArgumentConstraint withArgumentIndex(
      int argumentIndex, SimpleInliningConstraintFactory factory) {
    return factory.createConstConstraint(argumentIndex);
  }
}
