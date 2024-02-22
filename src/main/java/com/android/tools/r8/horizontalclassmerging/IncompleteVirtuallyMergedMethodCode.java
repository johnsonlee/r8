// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.horizontalclassmerging.VirtualMethodMerger.SuperMethodReference;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirEncodingStrategy;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.utils.BooleanUtils;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * A short-lived piece of code that will be converted into {@link LirCode} using the method {@link
 * IncompleteHorizontalClassMergerCode#toLirCode(AppView, ProgramMethod,
 * HorizontalClassMergerGraphLens)}.
 */
public class IncompleteVirtuallyMergedMethodCode extends IncompleteHorizontalClassMergerCode {

  private final DexField classIdField;
  private final Int2ReferenceSortedMap<DexMethod> mappedMethods;
  private final SuperMethodReference superMethod;

  public IncompleteVirtuallyMergedMethodCode(
      DexField classIdField,
      Int2ReferenceSortedMap<DexMethod> mappedMethods,
      SuperMethodReference superMethod) {
    this.mappedMethods = mappedMethods;
    this.classIdField = classIdField;
    this.superMethod = superMethod;
  }

  @Override
  public void addExtraUnusedArguments(int numberOfUnusedArguments) {
    throw new Unreachable();
  }

  public boolean hasSuperMethod() {
    return superMethod != null;
  }

  public SuperMethodReference getSuperMethod() {
    return superMethod;
  }

  @Override
  public LirCode<Integer> toLirCode(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      HorizontalClassMergerGraphLens lens) {
    LirEncodingStrategy<Value, Integer> strategy =
        LirStrategy.getDefaultStrategy().getEncodingStrategy();
    LirBuilder<Value, Integer> lirBuilder =
        LirCode.builder(
            method.getReference(),
            method.getDefinition().isD8R8Synthesized(),
            strategy,
            appView.options());

    int instructionIndex = 0;
    List<Value> argumentValues = new ArrayList<>();
    TypeElement returnType =
        method.getReturnType().isVoidType() ? null : method.getReturnType().toTypeElement(appView);

    // Add receiver argument.
    DexType receiverType = method.getHolderType();
    TypeElement receiverTypeElement = receiverType.toTypeElement(appView, definitelyNotNull());
    Value receiverValue = Value.createNoDebugLocal(instructionIndex, receiverTypeElement);
    argumentValues.add(receiverValue);
    strategy.defineValue(receiverValue, receiverValue.getNumber());
    lirBuilder.addArgument(receiverValue.getNumber(), false);
    instructionIndex++;

    // Add non-receiver arguments.
    for (; instructionIndex < method.getDefinition().getNumberOfArguments(); instructionIndex++) {
      DexType argumentType = method.getArgumentType(instructionIndex);
      TypeElement argumentTypeElement = argumentType.toTypeElement(appView);
      Value argumentValue = Value.createNoDebugLocal(instructionIndex, argumentTypeElement);
      argumentValues.add(argumentValue);
      strategy.defineValue(argumentValue, argumentValue.getNumber());
      lirBuilder.addArgument(argumentValue.getNumber(), argumentType.isBooleanType());
    }

    // Read class id field from receiver.
    TypeElement classIdValueType = TypeElement.getInt();
    Value classIdValue = Value.createNoDebugLocal(instructionIndex, classIdValueType);
    strategy.defineValue(classIdValue, classIdValue.getNumber());
    lirBuilder.addInstanceGet(classIdField, receiverValue);
    instructionIndex++;

    // Emit switch.
    IntBidirectionalIterator classIdIterator = mappedMethods.keySet().iterator();
    int[] keys = new int[mappedMethods.size() - BooleanUtils.intValue(!hasSuperMethod())];
    int[] targets = new int[keys.length];
    int nextTarget = instructionIndex - argumentValues.size() + 3;
    for (int i = 0; i < keys.length; i++) {
      keys[i] = classIdIterator.nextInt();
      targets[i] = nextTarget;
      nextTarget += 2;
    }
    lirBuilder.addIntSwitch(classIdValue, keys, targets);
    instructionIndex++;

    // Emit switch fallthrough.
    if (hasSuperMethod()) {
      lirBuilder.addInvokeSuper(
          superMethod.getRewrittenReference(lens, method), argumentValues, false);
    } else {
      DexMethod fallthroughTarget =
          lens.getNextMethodSignature(mappedMethods.get(mappedMethods.lastIntKey()));
      if (method.getHolder().isInterface()) {
        lirBuilder.addInvokeInterface(fallthroughTarget, argumentValues);
      } else {
        lirBuilder.addInvokeVirtual(fallthroughTarget, argumentValues);
      }
    }
    if (method.getReturnType().isVoidType()) {
      lirBuilder.addReturnVoid();
    } else {
      Value returnValue = Value.createNoDebugLocal(instructionIndex, returnType);
      strategy.defineValue(returnValue, returnValue.getNumber());
      lirBuilder.addReturn(returnValue);
    }
    instructionIndex += 2;

    // Emit switch cases.
    for (int classId : keys) {
      DexMethod target = lens.getNextMethodSignature(mappedMethods.get(classId));
      if (method.getHolder().isInterface()) {
        lirBuilder.addInvokeInterface(target, argumentValues);
      } else {
        lirBuilder.addInvokeVirtual(target, argumentValues);
      }
      if (method.getReturnType().isVoidType()) {
        lirBuilder.addReturnVoid();
      } else {
        Value returnValue = Value.createNoDebugLocal(instructionIndex, returnType);
        strategy.defineValue(returnValue, returnValue.getNumber());
        lirBuilder.addReturn(returnValue);
      }
      instructionIndex += 2;
    }

    return new LirCode<>(lirBuilder.build()) {

      @Override
      public boolean hasExplicitCodeLens() {
        return true;
      }

      @Override
      public GraphLens getCodeLens(AppView<?> appView) {
        return lens;
      }
    };
  }

  @Override
  public String toString() {
    return "IncompleteVirtuallyMergedMethodCode";
  }
}
