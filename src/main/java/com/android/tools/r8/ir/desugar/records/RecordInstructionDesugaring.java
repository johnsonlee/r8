// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.isInvokeDynamicOnRecord;
import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.parseInvokeDynamicOnRecord;

import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfOpcodeUtils;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.ProgramAdditions;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer.RecordInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.RecordInvokeDynamic;
import com.android.tools.r8.ir.synthetic.RecordCfCodeProvider.RecordEqCfCodeProvider;
import com.android.tools.r8.ir.synthetic.RecordCfCodeProvider.RecordGetFieldsAsObjectsCfCodeProvider;
import com.android.tools.r8.ir.synthetic.RecordCfCodeProvider.RecordHashCfCodeProvider;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class RecordInstructionDesugaring implements CfInstructionDesugaring {

  private static final int MAX_FIELDS_FOR_OUTLINE = 32;

  final AppView<?> appView;
  final DexItemFactory factory;
  private final List<DexType> orderedSharedTypes;
  private final DexProto recordToStringHelperProto;

  public static final String GET_FIELDS_AS_OBJECTS_METHOD_NAME = "$record$getFieldsAsObjects";
  public static final String HASH_CODE_METHOD_NAME = "$record$hashCode";
  public static final String EQUALS_RECORD_METHOD_NAME = "$record$equals";

  RecordInstructionDesugaring(AppView<?> appView) {
    this.appView = appView;
    factory = appView.dexItemFactory();
    orderedSharedTypes =
        ImmutableList.of(
            factory.booleanType,
            factory.intType,
            factory.longType,
            factory.floatType,
            factory.doubleType,
            factory.objectType);
    recordToStringHelperProto =
        factory.createProto(
            factory.stringType, factory.objectArrayType, factory.classType, factory.stringType);
  }

  public static RecordInstructionDesugaring create(AppView<?> appView) {
    switch (appView.options().desugarRecordState()) {
      case OFF:
        return null;
      case PARTIAL:
        return new RecordInstructionDesugaring(appView);
      case FULL:
        return new RecordFullInstructionDesugaring(appView);
    }
    throw new Unreachable();
  }

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    RecordCfMethods.registerSynthesizedCodeReferences(factory);
    RecordGetFieldsAsObjectsCfCodeProvider.registerSynthesizedCodeReferences(factory);
    RecordEqCfCodeProvider.registerSynthesizedCodeReferences(factory);
    RecordHashCfCodeProvider.registerSynthesizedCodeReferences(factory);
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfInvokeOpcodes(consumer);
    consumer.accept(Opcodes.INVOKEDYNAMIC);
  }

  @Override
  public void prepare(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramAdditions programAdditions) {
    CfCode cfCode = method.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : cfCode.getInstructions()) {
      if (instruction.isInvokeDynamic() && compute(instruction, method).needsDesugaring()) {
        prepareInvokeDynamicOnRecord(
            instruction.asInvokeDynamic(), programAdditions, method, eventConsumer);
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void prepareInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic,
      ProgramAdditions programAdditions,
      ProgramMethod context,
      RecordInstructionDesugaringEventConsumer eventConsumer) {
    RecordInvokeDynamic recordInvokeDynamic =
        parseInvokeDynamicOnRecord(invokeDynamic.getCallSite(), appView);
    if (recordInvokeDynamic.getMethodName() == factory.toStringMethodName) {
      ensureGetFieldsAsObjects(recordInvokeDynamic, programAdditions, context, eventConsumer);
      return;
    }
    if (recordInvokeDynamic.getMethodName() == factory.hashCodeMethodName) {
      if (!shouldOutlineMethods(recordInvokeDynamic.getRecordClass())) {
        ensureHashCodeMethod(recordInvokeDynamic, programAdditions, context, eventConsumer);
      }
      return;
    }
    if (recordInvokeDynamic.getMethodName() == factory.equalsMethodName) {
      ensureEqualsRecord(recordInvokeDynamic, programAdditions, context, eventConsumer);
      return;
    }
    throw new Unreachable("Invoke dynamic needs record desugaring but could not be desugared.");
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isInvokeDynamic()) {
      if (needsDesugaring(instruction.asInvokeDynamic(), context)) {
        return desugarInvokeDynamic(instruction);
      } else {
        return DesugarDescription.nothing();
      }
    }
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      boolean invokeSuper = cfInvoke.isInvokeSuper(context.getHolderType());
      if (needsDesugaring(cfInvoke.getMethod(), invokeSuper)) {
        DexMethod newMethod = rewriteMethod(cfInvoke.getMethod(), invokeSuper);
        assert newMethod != cfInvoke.getMethod();
        return desugarInvoke(cfInvoke, newMethod);
      } else {
        return DesugarDescription.nothing();
      }
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription desugarInvokeDynamic(CfInstruction instruction) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                desugarInvokeDynamicOnRecord(
                    instruction.asInvokeDynamic(),
                    freshLocalProvider,
                    localStackAllocator,
                    eventConsumer,
                    context,
                    methodProcessingContext))
        .build();
  }

  private DesugarDescription desugarInvoke(CfInvoke invoke, DexMethod newMethod) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                Collections.singletonList(
                    new CfInvoke(invoke.getOpcode(), newMethod, invoke.isInterface())))
        .build();
  }

  @SuppressWarnings("ReferenceEquality")
  private List<CfInstruction> desugarInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    RecordInvokeDynamic recordInvokeDynamic =
        parseInvokeDynamicOnRecord(invokeDynamic, appView, context);
    if (recordInvokeDynamic.getMethodName() == factory.toStringMethodName) {
      return desugarInvokeRecordToString(
          recordInvokeDynamic,
          localStackAllocator,
          eventConsumer,
          context,
          methodProcessingContext);
    }
    if (recordInvokeDynamic.getMethodName() == factory.hashCodeMethodName) {
      return desugarInvokeRecordHashCode(
          recordInvokeDynamic,
          freshLocalProvider,
          localStackAllocator,
          eventConsumer,
          context,
          methodProcessingContext);
    }
    if (recordInvokeDynamic.getMethodName() == factory.equalsMethodName) {
      return desugarInvokeRecordEquals(recordInvokeDynamic);
    }
    throw new Unreachable("Invoke dynamic needs record desugaring but could not be desugared.");
  }

  private ProgramMethod synthesizeGetFieldsAsObjectsMethod(
      DexProgramClass clazz, DexField[] fields, DexMethod method) {
    return synthesizeMethod(
        clazz,
        new RecordGetFieldsAsObjectsCfCodeProvider(appView, factory.recordTagType, fields),
        method);
  }

  private ProgramMethod synthesizeMethod(
      DexProgramClass clazz, SyntheticCfCodeProvider provider, DexMethod method) {
    MethodAccessFlags methodAccessFlags =
        MethodAccessFlags.fromSharedAccessFlags(
            Constants.ACC_SYNTHETIC | Constants.ACC_PRIVATE, false);
    DexEncodedMethod encodedMethod =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(method)
            .setAccessFlags(methodAccessFlags)
            // Will be traced by the enqueuer.
            .disableAndroidApiLevelCheck()
            .build();
    ProgramMethod result = new ProgramMethod(clazz, encodedMethod);
    result.setCode(provider.generateCfCode(), appView);
    return result;
  }

  private void ensureHashCodeMethod(
      RecordInvokeDynamic recordInvokeDynamic,
      ProgramAdditions programAdditions,
      ProgramMethod context,
      RecordInstructionDesugaringEventConsumer eventConsumer) {
    DexProgramClass clazz = recordInvokeDynamic.getRecordClass();
    DexMethod method = hashCodeMethod(clazz.type);
    assert clazz.lookupProgramMethod(method) == null;
    Pair<List<DexField>, List<DexType>> pair = sortedInstanceFields(clazz.instanceFields());
    ProgramMethod hashCodeHelperMethod =
        programAdditions.ensureMethod(
            method,
            () ->
                synthesizeMethod(
                    clazz,
                    new RecordHashCfCodeProvider(appView, clazz.type, pair.getFirst(), false),
                    method));
    eventConsumer.acceptRecordHashCodeHelperMethod(hashCodeHelperMethod, context);
  }

  private void ensureEqualsRecord(
      RecordInvokeDynamic recordInvokeDynamic,
      ProgramAdditions programAdditions,
      ProgramMethod context,
      RecordInstructionDesugaringEventConsumer eventConsumer) {
    DexProgramClass clazz = recordInvokeDynamic.getRecordClass();
    DexMethod method = equalsRecordMethod(clazz.type);
    assert clazz.lookupProgramMethod(method) == null;
    Pair<List<DexField>, List<DexType>> pair = sortedInstanceFields(clazz.instanceFields());
    ProgramMethod equalsHelperMethod =
        programAdditions.ensureMethod(
            method,
            () ->
                synthesizeMethod(
                    clazz,
                    new RecordEqCfCodeProvider(appView, clazz.type, pair.getFirst()),
                    method));
    eventConsumer.acceptRecordEqualsHelperMethod(equalsHelperMethod, context);
  }

  private DexMethod ensureGetFieldsAsObjects(
      RecordInvokeDynamic recordInvokeDynamic,
      ProgramAdditions programAdditions,
      ProgramMethod context,
      RecordInstructionDesugaringEventConsumer eventConsumer) {
    DexProgramClass clazz = recordInvokeDynamic.getRecordClass();
    DexMethod method = getFieldsAsObjectsMethod(clazz.type);
    assert clazz.lookupProgramMethod(method) == null;
    ProgramMethod getFieldsAsObjectsHelperMethod =
        programAdditions.ensureMethod(
            method,
            () ->
                synthesizeGetFieldsAsObjectsMethod(clazz, recordInvokeDynamic.getFields(), method));
    eventConsumer.acceptRecordGetFieldsAsObjectsHelperMethod(
        getFieldsAsObjectsHelperMethod, context);
    return method;
  }

  private DexMethod hashCodeMethod(DexType holder) {
    return factory.createMethod(
        holder, factory.createProto(factory.intType), HASH_CODE_METHOD_NAME);
  }

  private DexMethod getFieldsAsObjectsMethod(DexType holder) {
    return factory.createMethod(
        holder, factory.createProto(factory.objectArrayType), GET_FIELDS_AS_OBJECTS_METHOD_NAME);
  }

  private DexMethod equalsRecordMethod(DexType holder) {
    return factory.createMethod(
        holder,
        factory.createProto(factory.booleanType, factory.objectType),
        EQUALS_RECORD_METHOD_NAME);
  }

  private ProgramMethod synthesizeRecordHelper(
      DexProto helperProto,
      BiFunction<DexItemFactory, DexMethod, CfCode> codeGenerator,
      MethodProcessingContext methodProcessingContext) {
    return appView
        .getSyntheticItems()
        .createMethod(
            kinds -> kinds.RECORD_HELPER,
            methodProcessingContext.createUniqueContext(),
            appView,
            builder ->
                builder
                    .setProto(helperProto)
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(methodSig -> codeGenerator.apply(appView.dexItemFactory(), methodSig))
                    .disableAndroidApiLevelCheck());
  }

  private boolean shouldOutlineMethods(DexClass recordClass) {
    return recordClass.instanceFields().size() < MAX_FIELDS_FOR_OUTLINE;
  }

  public static int fixedHashCodeForEmptyRecord() {
    return 0;
  }

  // Answers an ordered map of the fields with the type for the hashCode proto.
  private Pair<List<DexField>, List<DexType>> sortedInstanceFields(
      List<DexEncodedField> instanceFields) {
    Map<DexType, List<DexField>> temp = new IdentityHashMap<>();
    for (DexEncodedField instanceField : instanceFields) {
      DexType protoType =
          instanceField.getType().isBooleanType()
              ? instanceField.getType()
              : ValueType.fromDexType(instanceField.getType()).toDexType(factory);
      temp.computeIfAbsent(protoType, ignored -> new ArrayList<>())
          .add(instanceField.getReference());
    }
    Pair<List<DexField>, List<DexType>> pair = new Pair<>(new ArrayList<>(), new ArrayList<>());
    for (DexType orderedSharedType : orderedSharedTypes) {
      List<DexField> dexFields = temp.get(orderedSharedType);
      if (dexFields != null) {
        for (DexField dexField : dexFields) {
          pair.getFirst().add(dexField);
          pair.getSecond().add(orderedSharedType);
        }
      }
    }
    assert pair.getFirst().size() == instanceFields.size();
    assert pair.getSecond().size() == instanceFields.size();
    return pair;
  }

  private List<CfInstruction> desugarInvokeRecordHashCode(
      RecordInvokeDynamic recordInvokeDynamic,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      RecordInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    ArrayList<CfInstruction> instructions = new ArrayList<>();
    DexProgramClass recordClass = recordInvokeDynamic.getRecordClass();
    DexMethod hashCodeMethod = hashCodeMethod(recordInvokeDynamic.getRecordType());
    if (recordClass.instanceFields().isEmpty()) {
      localStackAllocator.allocateLocalStack(1);
      instructions.add(new CfConstNumber(fixedHashCodeForEmptyRecord(), ValueType.INT));
      return instructions;
    }
    if (shouldOutlineMethods(recordClass)) {
      Pair<List<DexField>, List<DexType>> sortedFields =
          sortedInstanceFields(recordClass.instanceFields());
      int freshLocal = freshLocalProvider.getFreshLocal(ValueType.OBJECT.requiredRegisters());
      instructions.add(new CfStackInstruction(Opcode.Dup));
      instructions.add(new CfStore(ValueType.OBJECT, freshLocal));
      DexField field = sortedFields.getFirst().get(0);
      int extraStack = field.getType().getRequiredRegisters();
      instructions.add(new CfInstanceFieldRead(field));
      for (int i = 1; i < sortedFields.getFirst().size(); i++) {
        instructions.add(new CfLoad(ValueType.OBJECT, freshLocal));
        field = sortedFields.getFirst().get(i);
        instructions.add(new CfInstanceFieldRead(field));
        extraStack += field.getType().getRequiredRegisters();
      }
      localStackAllocator.allocateLocalStack(extraStack);
      ProgramMethod method =
          appView
              .getSyntheticItems()
              .createMethod(
                  kinds -> kinds.RECORD_HELPER,
                  methodProcessingContext.createUniqueContext(),
                  appView,
                  builder ->
                      builder
                          .setProto(
                              factory.createProto(
                                  factory.intType, new ArrayList<>(sortedFields.getSecond())))
                          .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                          .setCode(
                              methodSig ->
                                  new RecordHashCfCodeProvider(
                                          appView,
                                          recordClass.getType(),
                                          sortedFields.getFirst(),
                                          true)
                                      .generateCfCode())
                          .disableAndroidApiLevelCheck());
      eventConsumer.acceptRecordHashCodeHelperMethod(method, context);
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method.getReference(), false));
    } else {
      assert recordClass.lookupProgramMethod(hashCodeMethod) != null;
      instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, hashCodeMethod, false));
    }
    return instructions;
  }

  private List<CfInstruction> desugarInvokeRecordEquals(RecordInvokeDynamic recordInvokeDynamic) {
    ArrayList<CfInstruction> instructions = new ArrayList<>();
    DexProgramClass recordClass = recordInvokeDynamic.getRecordClass();
    DexMethod equalsMethod = equalsRecordMethod(recordInvokeDynamic.getRecordType());
    assert recordClass.lookupProgramMethod(equalsMethod) != null;
    instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, equalsMethod, false));
    return instructions;
  }

  private List<CfInstruction> desugarInvokeRecordToString(
      RecordInvokeDynamic recordInvokeDynamic,
      LocalStackAllocator localStackAllocator,
      RecordInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    localStackAllocator.allocateLocalStack(2);
    DexMethod getFieldsAsObjects = getFieldsAsObjectsMethod(recordInvokeDynamic.getRecordType());
    assert recordInvokeDynamic.getRecordClass().lookupProgramMethod(getFieldsAsObjects) != null;
    ArrayList<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfInvoke(Opcodes.INVOKESPECIAL, getFieldsAsObjects, false));
    instructions.add(new CfConstClass(recordInvokeDynamic.getRecordType(), true));
    if (appView.enableWholeProgramOptimizations()) {
      instructions.add(
          new CfDexItemBasedConstString(
              recordInvokeDynamic.getRecordType(),
              recordInvokeDynamic.computeRecordFieldNamesComputationInfo()));
    } else {
      instructions.add(new CfConstString(recordInvokeDynamic.getFieldNames()));
    }
    ProgramMethod programMethod =
        synthesizeRecordHelper(
            recordToStringHelperProto,
            RecordCfMethods::RecordMethods_toString,
            methodProcessingContext);
    eventConsumer.acceptRecordToStringHelperMethod(programMethod, context);
    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, programMethod.getReference(), false));
    return instructions;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean needsDesugaring(DexMethod method, boolean isSuper) {
    return rewriteMethod(method, isSuper) != method;
  }

  private boolean needsDesugaring(CfInvokeDynamic invokeDynamic, ProgramMethod context) {
    return isInvokeDynamicOnRecord(invokeDynamic, appView, context);
  }

  @SuppressWarnings({"ConstantConditions", "ReferenceEquality"})
  private DexMethod rewriteMethod(DexMethod method, boolean isSuper) {
    if (!(method == factory.recordMembers.equals
        || method == factory.recordMembers.hashCode
        || method == factory.recordMembers.toString)) {
      return method;
    }
    if (isSuper) {
      // TODO(b/179146128): Support rewriting invoke-super to a Record method.
      throw new CompilationError("Rewrite invoke-super to abstract method error.");
    }
    if (method == factory.recordMembers.equals) {
      return factory.objectMembers.equals;
    }
    if (method == factory.recordMembers.toString) {
      return factory.objectMembers.toString;
    }
    assert method == factory.recordMembers.hashCode;
    return factory.objectMembers.hashCode;
  }
}
