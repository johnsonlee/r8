// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleConstValue;
import com.android.tools.r8.ir.analysis.value.SingleDexItemBasedStringValue;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirEncodingStrategy;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

/**
 * Similar to CfCode, but with a marker that makes it possible to recognize this is synthesized by
 * the horizontal class merger.
 */
public class IncompleteMergedInstanceInitializerCode extends IncompleteHorizontalClassMergerCode {

  private final DexField classIdField;
  private int numberOfUnusedArguments;

  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre;
  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost;

  private final DexMethod parentConstructor;
  private final List<InstanceFieldInitializationInfo> parentConstructorArguments;

  IncompleteMergedInstanceInitializerCode(
      DexField classIdField,
      int numberOfUnusedArguments,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost,
      DexMethod parentConstructor,
      List<InstanceFieldInitializationInfo> parentConstructorArguments) {
    this.classIdField = classIdField;
    this.numberOfUnusedArguments = numberOfUnusedArguments;
    this.instanceFieldAssignmentsPre = instanceFieldAssignmentsPre;
    this.instanceFieldAssignmentsPost = instanceFieldAssignmentsPost;
    this.parentConstructor = parentConstructor;
    this.parentConstructorArguments = parentConstructorArguments;
  }

  @Override
  public void addExtraUnusedArguments(int numberOfUnusedArguments) {
    this.numberOfUnusedArguments += numberOfUnusedArguments;
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

    // Assign class id.
    if (classIdField != null) {
      Value classIdValue = argumentValues.get(argumentValues.size() - 1 - numberOfUnusedArguments);
      lirBuilder.addInstancePut(
          lens.getNextFieldSignature(classIdField), receiverValue, classIdValue);
      instructionIndex++;
    }

    // Assign each field.
    instructionIndex =
        addLirInstructionsForInstanceFieldAssignments(
            appView,
            method,
            lirBuilder,
            strategy,
            argumentValues,
            instructionIndex,
            instanceFieldAssignmentsPre,
            lens);

    // Load constructor arguments.
    MethodLookupResult parentConstructorLookup =
        lens.lookupInvokeDirect(parentConstructor, method, appView.codeLens());
    List<Value> parentConstructorArgumentValues = new ArrayList<>();
    parentConstructorArgumentValues.add(receiverValue);
    int parentConstructorArgumentIndex = 0;
    for (InstanceFieldInitializationInfo initializationInfo : parentConstructorArguments) {
      instructionIndex =
          addLirInstructionsForInitializationInfo(
              appView,
              lirBuilder,
              strategy,
              initializationInfo,
              argumentValues,
              instructionIndex,
              parentConstructorLookup.getReference().getParameter(parentConstructorArgumentIndex),
              parentConstructorArgumentValues::add);
      parentConstructorArgumentIndex++;
    }

    for (ExtraParameter extraParameter :
        parentConstructorLookup.getPrototypeChanges().getExtraParameters()) {
      instructionIndex =
          addLirInstructionsForInitializationInfo(
              appView,
              lirBuilder,
              strategy,
              extraParameter.getValue(appView),
              argumentValues,
              instructionIndex,
              parentConstructorLookup.getReference().getParameter(parentConstructorArgumentIndex),
              parentConstructorArgumentValues::add);
      parentConstructorArgumentIndex++;
    }

    // Invoke parent constructor.
    lirBuilder.addInvokeDirect(
        parentConstructorLookup.getReference(), parentConstructorArgumentValues, false);
    instructionIndex++;

    // Assign each field.
    addLirInstructionsForInstanceFieldAssignments(
        appView,
        method,
        lirBuilder,
        strategy,
        argumentValues,
        instructionIndex,
        instanceFieldAssignmentsPost,
        lens);

    // Return.
    lirBuilder.addReturnVoid();
    instructionIndex++;

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

  private static int addLirInstructionsForInstanceFieldAssignments(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      LirBuilder<Value, Integer> lirBuilder,
      LirEncodingStrategy<Value, Integer> strategy,
      List<Value> argumentValues,
      int instructionIndex,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignments,
      HorizontalClassMergerGraphLens lens) {
    for (Entry<DexField, InstanceFieldInitializationInfo> entry :
        instanceFieldAssignments.entrySet()) {
      DexField field = entry.getKey();
      InstanceFieldInitializationInfo initializationInfo = entry.getValue();

      // Load the field value and then set the field.
      Box<Value> fieldValueBox = new Box<>();
      instructionIndex =
          addLirInstructionsForInitializationInfo(
              appView,
              lirBuilder,
              strategy,
              initializationInfo,
              argumentValues,
              instructionIndex,
              field.getType(),
              fieldValueBox::set);
      Value fieldValue = fieldValueBox.get();

      // Insert a check to ensure the program continues to type check according to Java type
      // checking. Otherwise, instance initializer merging may cause open interfaces. If
      // <init>(A) and <init>(B) both have the behavior `this.i = arg; this.j = arg` where the
      // type of `i` is I and the type of `j` is J, and both A and B implements I and J, then
      // the constructors are merged into a single constructor <init>(java.lang.Object), which
      // is no longer strictly type checking. Note that no choice of parameter type would solve
      // this.
      DexField rewrittenField = lens.getNextFieldSignature(field);
      if (initializationInfo.isArgumentInitializationInfo()) {
        int argumentIndex = initializationInfo.asArgumentInitializationInfo().getArgumentIndex();
        if (argumentIndex > 0) {
          DexType argumentType = method.getArgumentType(argumentIndex);
          if (argumentType.isClassType()
              && !appView.appInfo().isSubtype(argumentType, rewrittenField.getType())) {
            TypeElement newFieldValueTypeElement =
                rewrittenField.getType().toTypeElement(appView, fieldValue.getType().nullability());
            Value newFieldValue =
                Value.createNoDebugLocal(instructionIndex, newFieldValueTypeElement);
            strategy.defineValue(newFieldValue, newFieldValue.getNumber());
            lirBuilder.addSafeCheckCast(rewrittenField.getType(), fieldValue);
            fieldValue = newFieldValue;
            instructionIndex++;
          }
        }
      }

      lirBuilder.addInstancePut(rewrittenField, ListUtils.first(argumentValues), fieldValue);
      instructionIndex++;
    }
    return instructionIndex;
  }

  private static int addLirInstructionsForInitializationInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      LirBuilder<Value, Integer> lirBuilder,
      LirEncodingStrategy<Value, Integer> strategy,
      InstanceFieldInitializationInfo initializationInfo,
      List<Value> argumentValues,
      int instructionIndex,
      DexType type,
      Consumer<Value> valueConsumer) {
    Value value;
    if (initializationInfo.isArgumentInitializationInfo()) {
      int argumentIndex = initializationInfo.asArgumentInitializationInfo().getArgumentIndex();
      value = argumentValues.get(argumentIndex);
    } else {
      assert initializationInfo.isSingleValue();
      assert initializationInfo.asSingleValue().isSingleConstValue();
      SingleConstValue singleConstValue = initializationInfo.asSingleValue().asSingleConstValue();
      TypeElement valueTypeElement;
      if (singleConstValue.isSingleConstClassValue()) {
        DexType classType = singleConstValue.asSingleConstClassValue().getType();
        lirBuilder.addConstClass(classType, false);
        valueTypeElement = TypeElement.classClassType(appView, definitelyNotNull());
      } else if (singleConstValue.isSingleDexItemBasedStringValue()) {
        SingleDexItemBasedStringValue dexItemBasedStringValue =
            singleConstValue.asSingleDexItemBasedStringValue();
        lirBuilder.addDexItemBasedConstString(
            dexItemBasedStringValue.getItem(), dexItemBasedStringValue.getNameComputationInfo());
        valueTypeElement = TypeElement.stringClassType(appView, definitelyNotNull());
      } else if (singleConstValue.isNull()) {
        assert type.isReferenceType();
        lirBuilder.addConstNull();
        valueTypeElement = TypeElement.getNull();
      } else if (singleConstValue.isSingleNumberValue()) {
        assert type.isPrimitiveType();
        long numberValue = singleConstValue.asSingleNumberValue().getValue();
        lirBuilder.addConstNumber(ValueType.fromDexType(type), numberValue);
        valueTypeElement = type.toTypeElement(appView);
      } else {
        assert singleConstValue.isSingleStringValue();
        DexString string = singleConstValue.asSingleStringValue().getDexString();
        lirBuilder.addConstString(string);
        valueTypeElement = TypeElement.stringClassType(appView, definitelyNotNull());
      }
      value = Value.createNoDebugLocal(instructionIndex, valueTypeElement);
      strategy.defineValue(value, value.getNumber());
      instructionIndex++;
    }
    valueConsumer.accept(value);
    return instructionIndex;
  }

  @Override
  public String toString() {
    return "IncompleteMergedInstanceInitializerCode";
  }
}
