// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramFieldMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class FieldStateCollection {

  private final ProgramFieldMap<NonEmptyValueState> fieldStates;

  private FieldStateCollection(ProgramFieldMap<NonEmptyValueState> fieldStates) {
    this.fieldStates = fieldStates;
  }

  public static FieldStateCollection createConcurrent() {
    return new FieldStateCollection(ProgramFieldMap.createConcurrent());
  }

  public NonEmptyValueState addTemporaryFieldState(
      AppView<AppInfoWithLiveness> appView,
      ProgramField field,
      Supplier<NonEmptyValueState> fieldStateSupplier,
      Timing timing) {
    return addTemporaryFieldState(
        field,
        fieldStateSupplier,
        timing,
        (existingFieldState, fieldStateToAdd) -> {
          DexType inStaticType = null;
          return existingFieldState.mutableJoin(
              appView,
              fieldStateToAdd,
              inStaticType,
              field.getType(),
              StateCloner.getCloner(),
              Action.empty());
        });
  }

  /**
   * This intentionally takes a {@link Supplier<NonEmptyValueState>} to avoid computing the field
   * state for a given field put when nothing is known about the value of the field.
   */
  public NonEmptyValueState addTemporaryFieldState(
      ProgramField field,
      Supplier<NonEmptyValueState> fieldStateSupplier,
      Timing timing,
      BiFunction<ConcreteValueState, ConcreteValueState, NonEmptyValueState> joiner) {
    return addTemporaryFieldState(field, fieldStateSupplier, timing, joiner, Action.empty());
  }

  public NonEmptyValueState addTemporaryFieldState(
      ProgramField field,
      Supplier<NonEmptyValueState> fieldStateSupplier,
      Timing timing,
      BiFunction<ConcreteValueState, ConcreteValueState, NonEmptyValueState> joiner,
      Action unknownTransitionHandler) {
    ValueState joinState =
        fieldStates.compute(
            field,
            (f, existingFieldState) -> {
              if (existingFieldState == null) {
                NonEmptyValueState result = fieldStateSupplier.get();
                if (result.isUnknown()) {
                  unknownTransitionHandler.execute();
                }
                return result;
              }
              assert !existingFieldState.isBottom();
              if (existingFieldState.isUnknown()) {
                return existingFieldState;
              }
              NonEmptyValueState fieldStateToAdd = fieldStateSupplier.get();
              if (fieldStateToAdd.isUnknown()) {
                unknownTransitionHandler.execute();
                return fieldStateToAdd;
              }
              timing.begin("Join temporary field state");
              ConcreteValueState existingConcreteFieldState = existingFieldState.asConcrete();
              ConcreteValueState concreteFieldStateToAdd = fieldStateToAdd.asConcrete();
              NonEmptyValueState joinResult =
                  joiner.apply(existingConcreteFieldState, concreteFieldStateToAdd);
              if (joinResult.isUnknown()) {
                unknownTransitionHandler.execute();
              }
              timing.end();
              return joinResult;
            });
    assert joinState.isNonEmpty();
    return joinState.asNonEmpty();
  }

  public void forEach(BiConsumer<ProgramField, ValueState> consumer) {
    fieldStates.forEach(consumer);
  }

  public ValueState get(ProgramField field) {
    NonEmptyValueState fieldState = fieldStates.get(field);
    return fieldState != null ? fieldState : ValueState.bottom(field);
  }

  public ValueState remove(ProgramField field) {
    ValueState removed = fieldStates.remove(field);
    return removed != null ? removed : ValueState.bottom(field);
  }

  public ValueState set(ProgramField field, ValueState state) {
    if (state.isNonEmpty()) {
      return set(field, state.asNonEmpty());
    }
    return remove(field);
  }

  public ValueState set(ProgramField field, NonEmptyValueState state) {
    NonEmptyValueState previous = fieldStates.put(field, state);
    return previous != null ? previous : ValueState.bottom(field);
  }
}
