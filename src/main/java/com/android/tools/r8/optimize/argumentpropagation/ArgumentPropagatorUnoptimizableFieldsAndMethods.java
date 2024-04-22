// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.UnknownMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.classhierarchy.MethodOverridesCollector;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Collection;

public class ArgumentPropagatorUnoptimizableFieldsAndMethods {

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final FieldStateCollection fieldStates;
  private final MethodStateCollectionByReference methodStates;

  public ArgumentPropagatorUnoptimizableFieldsAndMethods(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      FieldStateCollection fieldStates,
      MethodStateCollectionByReference methodStates) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.fieldStates = fieldStates;
    this.methodStates = methodStates;
  }

  public void run(Collection<DexProgramClass> stronglyConnectedComponent) {
    initializeUnoptimizableFieldStates(stronglyConnectedComponent);
    initializeUnoptimizableMethodStates(stronglyConnectedComponent);
  }

  private void initializeUnoptimizableFieldStates(
      Collection<DexProgramClass> stronglyConnectedComponent) {
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      clazz.forEachProgramField(
          field -> {
            if (isUnoptimizableField(field)) {
              disableValuePropagationForField(field);
            }
          });
    }
  }

  // TODO(b/190154391): Consider if we should bail out for classes that inherit from a missing
  //  class.
  private void initializeUnoptimizableMethodStates(
      Collection<DexProgramClass> stronglyConnectedComponent) {
    ProgramMethodSet unoptimizableVirtualMethods =
        MethodOverridesCollector.findAllMethodsAndOverridesThatMatches(
            appView,
            immediateSubtypingInfo,
            stronglyConnectedComponent,
            method -> {
              if (isUnoptimizableMethod(method)) {
                if (method.getDefinition().belongsToVirtualPool()
                    && !method.getHolder().isFinal()
                    && !method.getAccessFlags().isFinal()) {
                  return true;
                } else {
                  disableValuePropagationForMethodParameters(method);
                }
              }
              return false;
            });
    unoptimizableVirtualMethods.forEach(this::disableValuePropagationForMethodParameters);
  }

  private void disableValuePropagationForField(ProgramField field) {
    fieldStates.set(field, ValueState.unknown());
  }

  private void disableValuePropagationForMethodParameters(ProgramMethod method) {
    methodStates.set(method, UnknownMethodState.get());
  }

  private boolean isUnoptimizableField(ProgramField field) {
    KeepFieldInfo keepInfo = appView.getKeepInfo(field);
    InternalOptions options = appView.options();
    if (!keepInfo.isFieldPropagationAllowed(options)) {
      return true;
    }
    FieldAccessInfo fieldAccessInfo =
        appView.appInfo().getFieldAccessInfoCollection().get(field.getReference());
    return fieldAccessInfo.hasReflectiveWrite() || fieldAccessInfo.isWrittenFromMethodHandle();
  }

  private boolean isUnoptimizableMethod(ProgramMethod method) {
    assert !method.getDefinition().belongsToVirtualPool()
            || !method.getDefinition().isLibraryMethodOverride().isUnknown()
        : "Unexpected virtual method without library method override information: "
            + method.toSourceString();
    InternalOptions options = appView.options();
    return method.getDefinition().isLibraryMethodOverride().isPossiblyTrue()
        || !appView.getKeepInfo(method).isArgumentPropagationAllowed(options);
  }
}
