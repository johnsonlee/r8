// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfSafeCheckCast;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCodeWithLens;
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
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirEncodingStrategy;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;

/**
 * Similar to CfCode, but with a marker that makes it possible to recognize this is synthesized by
 * the horizontal class merger.
 */
public class IncompleteMergedInstanceInitializerCode extends IncompleteHorizontalClassMergerCode {

  private final DexField classIdField;
  private final int extraNulls;
  private final DexMethod originalMethodReference;

  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre;
  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost;

  private final DexMethod parentConstructor;
  private final List<InstanceFieldInitializationInfo> parentConstructorArguments;

  IncompleteMergedInstanceInitializerCode(
      DexField classIdField,
      int extraNulls,
      DexMethod originalMethodReference,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost,
      DexMethod parentConstructor,
      List<InstanceFieldInitializationInfo> parentConstructorArguments) {
    this.classIdField = classIdField;
    this.extraNulls = extraNulls;
    this.originalMethodReference = originalMethodReference;
    this.instanceFieldAssignmentsPre = instanceFieldAssignmentsPre;
    this.instanceFieldAssignmentsPost = instanceFieldAssignmentsPost;
    this.parentConstructor = parentConstructor;
    this.parentConstructorArguments = parentConstructorArguments;
  }

  @Override
  public CfCode toCfCode(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      HorizontalClassMergerGraphLens lens) {
    int[] argumentToLocalIndex = new int[method.getDefinition().getNumberOfArguments()];
    int maxLocals = 0;
    for (int argumentIndex = 0; argumentIndex < argumentToLocalIndex.length; argumentIndex++) {
      argumentToLocalIndex[argumentIndex] = maxLocals;
      maxLocals += method.getArgumentType(argumentIndex).getRequiredRegisters();
    }

    IntBox maxStack = new IntBox();
    ImmutableList.Builder<CfInstruction> instructionBuilder = ImmutableList.builder();

    Position preamblePosition =
        SyntheticPosition.builder()
            .setLine(0)
            .setMethod(method.getReference())
            .setIsD8R8Synthesized(method.getDefinition().isD8R8Synthesized())
            .build();
    CfPosition position = new CfPosition(new CfLabel(), preamblePosition);
    instructionBuilder.add(position);
    instructionBuilder.add(position.getLabel());

    // Assign class id.
    if (classIdField != null) {
      int classIdLocalIndex = maxLocals - 1 - extraNulls;
      instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));
      instructionBuilder.add(new CfLoad(ValueType.INT, classIdLocalIndex));
      instructionBuilder.add(new CfInstanceFieldWrite(lens.getNextFieldSignature(classIdField)));
      maxStack.set(2);
    }

    // Assign each field.
    addCfInstructionsForInstanceFieldAssignments(
        appView,
        method,
        instructionBuilder,
        instanceFieldAssignmentsPre,
        argumentToLocalIndex,
        maxStack,
        lens);

    // Load receiver for parent constructor call.
    int stackHeightForParentConstructorCall = 1;
    instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));

    // Load constructor arguments.
    MethodLookupResult parentConstructorLookup =
        lens.lookupInvokeDirect(parentConstructor, method, appView.codeLens());

    int i = 0;
    for (InstanceFieldInitializationInfo initializationInfo : parentConstructorArguments) {
      stackHeightForParentConstructorCall +=
          addCfInstructionsForInitializationInfo(
              instructionBuilder,
              initializationInfo,
              argumentToLocalIndex,
              parentConstructorLookup.getReference().getParameter(i));
      i++;
    }

    for (ExtraParameter extraParameter :
        parentConstructorLookup.getPrototypeChanges().getExtraParameters()) {
      stackHeightForParentConstructorCall +=
          addCfInstructionsForInitializationInfo(
              instructionBuilder,
              extraParameter.getValue(appView),
              argumentToLocalIndex,
              parentConstructorLookup.getReference().getParameter(i));
      i++;
    }

    assert i == parentConstructorLookup.getReference().getParameters().size();

    // Invoke parent constructor.
    instructionBuilder.add(
        new CfInvoke(Opcodes.INVOKESPECIAL, parentConstructorLookup.getReference(), false));
    maxStack.setMax(stackHeightForParentConstructorCall);

    // Assign each field.
    addCfInstructionsForInstanceFieldAssignments(
        appView,
        method,
        instructionBuilder,
        instanceFieldAssignmentsPost,
        argumentToLocalIndex,
        maxStack,
        lens);

    // Return.
    instructionBuilder.add(new CfReturnVoid());

    return new CfCodeWithLens(
        lens,
        originalMethodReference.getHolderType(),
        maxStack.get(),
        maxLocals,
        instructionBuilder.build());
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
                appView.options())
            .setMetadata(IRMetadata.unknown());

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
      Value classIdValue = argumentValues.get(argumentValues.size() - 1 - extraNulls);
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
      public GraphLens getCodeLens(AppView<?> appView) {
        return lens;
      }
    };
  }

  private static void addCfInstructionsForInstanceFieldAssignments(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      ImmutableList.Builder<CfInstruction> instructionBuilder,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignments,
      int[] argumentToLocalIndex,
      IntBox maxStack,
      HorizontalClassMergerGraphLens lens) {
    instanceFieldAssignments.forEach(
        (field, initializationInfo) -> {
          // Load the receiver, the field value, and then set the field.
          instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));
          int stackSizeForInitializationInfo =
              addCfInstructionsForInitializationInfo(
                  instructionBuilder, initializationInfo, argumentToLocalIndex, field.getType());
          DexField rewrittenField = lens.getNextFieldSignature(field);

          // Insert a check to ensure the program continues to type check according to Java type
          // checking. Otherwise, instance initializer merging may cause open interfaces. If
          // <init>(A) and <init>(B) both have the behavior `this.i = arg; this.j = arg` where the
          // type of `i` is I and the type of `j` is J, and both A and B implements I and J, then
          // the constructors are merged into a single constructor <init>(java.lang.Object), which
          // is no longer strictly type checking. Note that no choice of parameter type would solve
          // this.
          if (initializationInfo.isArgumentInitializationInfo()) {
            int argumentIndex =
                initializationInfo.asArgumentInitializationInfo().getArgumentIndex();
            if (argumentIndex > 0) {
              DexType argumentType = method.getArgumentType(argumentIndex);
              if (argumentType.isClassType()
                  && !appView.appInfo().isSubtype(argumentType, rewrittenField.getType())) {
                instructionBuilder.add(new CfSafeCheckCast(rewrittenField.getType()));
              }
            }
          }

          instructionBuilder.add(new CfInstanceFieldWrite(rewrittenField));
          maxStack.setMax(stackSizeForInitializationInfo + 1);
        });
  }

  private static int addCfInstructionsForInitializationInfo(
      ImmutableList.Builder<CfInstruction> instructionBuilder,
      InstanceFieldInitializationInfo initializationInfo,
      int[] argumentToLocalIndex,
      DexType type) {
    if (initializationInfo.isArgumentInitializationInfo()) {
      int argumentIndex = initializationInfo.asArgumentInitializationInfo().getArgumentIndex();
      instructionBuilder.add(
          new CfLoad(ValueType.fromDexType(type), argumentToLocalIndex[argumentIndex]));
      return type.getRequiredRegisters();
    }

    assert initializationInfo.isSingleValue();
    assert initializationInfo.asSingleValue().isSingleConstValue();

    SingleConstValue singleConstValue = initializationInfo.asSingleValue().asSingleConstValue();
    if (singleConstValue.isSingleConstClassValue()) {
      instructionBuilder.add(
          new CfConstClass(singleConstValue.asSingleConstClassValue().getType()));
      return 1;
    } else if (singleConstValue.isSingleDexItemBasedStringValue()) {
      SingleDexItemBasedStringValue dexItemBasedStringValue =
          singleConstValue.asSingleDexItemBasedStringValue();
      instructionBuilder.add(
          new CfDexItemBasedConstString(
              dexItemBasedStringValue.getItem(), dexItemBasedStringValue.getNameComputationInfo()));
      return 1;
    } else if (singleConstValue.isNull()) {
      assert type.isReferenceType();
      instructionBuilder.add(new CfConstNull());
      return 1;
    } else if (singleConstValue.isSingleNumberValue()) {
      assert type.isPrimitiveType();
      instructionBuilder.add(
          new CfConstNumber(
              singleConstValue.asSingleNumberValue().getValue(), ValueType.fromDexType(type)));
      return type.getRequiredRegisters();
    } else {
      assert singleConstValue.isSingleStringValue();
      instructionBuilder.add(
          new CfConstString(singleConstValue.asSingleStringValue().getDexString()));
      return 1;
    }
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
