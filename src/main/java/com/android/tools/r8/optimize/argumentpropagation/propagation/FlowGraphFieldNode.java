// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteArrayTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;

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

  void addDefaultValue(AppView<AppInfoWithLiveness> appView, Action onChangedAction) {
    AbstractValueFactory abstractValueFactory = appView.abstractValueFactory();
    AbstractValue defaultValue;
    if (field.getAccessFlags().isStatic() && field.getDefinition().hasExplicitStaticValue()) {
      defaultValue = field.getDefinition().getStaticValue().toAbstractValue(abstractValueFactory);
    } else if (field.getType().isPrimitiveType()) {
      defaultValue = abstractValueFactory.createZeroValue();
    } else {
      defaultValue = abstractValueFactory.createUncheckedNullValue();
    }
    NonEmptyValueState fieldStateToAdd;
    if (field.getType().isArrayType()) {
      Nullability defaultNullability = Nullability.definitelyNull();
      fieldStateToAdd = ConcreteArrayTypeValueState.create(defaultNullability);
    } else if (field.getType().isClassType()) {
      assert defaultValue.isNull() || defaultValue.isSingleStringValue();
      DynamicType dynamicType =
          defaultValue.isNull()
              ? DynamicType.definitelyNull()
              : DynamicType.createExact(
                  TypeElement.stringClassType(appView, Nullability.definitelyNotNull()));
      fieldStateToAdd = ConcreteClassTypeValueState.create(defaultValue, dynamicType);
    } else {
      assert field.getType().isPrimitiveType();
      fieldStateToAdd = ConcretePrimitiveTypeValueState.create(defaultValue);
    }
    if (fieldStateToAdd.isConcrete()) {
      addState(appView, fieldStateToAdd.asConcrete(), onChangedAction);
    } else {
      // We should always be able to map static field values to an unknown abstract value.
      assert false;
      setStateToUnknown();
      onChangedAction.execute();
    }
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
