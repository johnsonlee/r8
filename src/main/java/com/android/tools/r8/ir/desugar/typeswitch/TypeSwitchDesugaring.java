// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import static com.android.tools.r8.ir.desugar.typeswitch.TypeSwitchDesugaringHelper.extractEnumField;
import static com.android.tools.r8.ir.desugar.typeswitch.TypeSwitchDesugaringHelper.getEnumField;
import static com.android.tools.r8.ir.desugar.typeswitch.TypeSwitchDesugaringHelper.isEnumSwitchCallSite;
import static com.android.tools.r8.ir.desugar.typeswitch.TypeSwitchDesugaringHelper.isTypeSwitchCallSite;

import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class TypeSwitchDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;

  private final DexProto switchHelperProto;
  private final DexType matchException;
  private final DexMethod matchExceptionInit;
  private final DexItemFactory factory;

  public TypeSwitchDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    switchHelperProto =
        factory.createProto(
            factory.intType, factory.objectType, factory.intType, factory.objectArrayType);
    matchException = factory.createType("Ljava/lang/MatchException;");
    matchExceptionInit =
        factory.createInstanceInitializer(
            matchException, factory.stringType, factory.throwableType);
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    consumer.accept(Opcodes.INVOKEDYNAMIC);
    consumer.accept(Opcodes.INVOKESPECIAL);
    consumer.accept(Opcodes.NEW);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvokeDynamic()) {
      // We need to replace the new MatchException with RuntimeException.
      // TODO(b/340800750): Consider using a more specific exception than RuntimeException, such
      //  as com.android.tools.r8.DesugarMatchException.
      if (instruction.isNew() && instruction.asNew().getType().isIdenticalTo(matchException)) {
        return DesugarDescription.builder()
            .setDesugarRewrite(
                (position,
                    freshLocalProvider,
                    localStackAllocator,
                    desugaringInfo,
                    eventConsumer,
                    theContext,
                    methodProcessingContext,
                    desugaringCollection,
                    dexItemFactory) -> ImmutableList.of(new CfNew(factory.runtimeExceptionType)))
            .build();
      }
      if (instruction.isInvokeSpecial()
          && instruction.asInvoke().getMethod().isIdenticalTo(matchExceptionInit)) {
        return DesugarDescription.builder()
            .setDesugarRewrite(
                (position,
                    freshLocalProvider,
                    localStackAllocator,
                    desugaringInfo,
                    eventConsumer,
                    theContext,
                    methodProcessingContext,
                    desugaringCollection,
                    dexItemFactory) ->
                    ImmutableList.of(
                        new CfInvoke(
                            Opcodes.INVOKESPECIAL,
                            factory.createInstanceInitializer(
                                factory.runtimeExceptionType,
                                factory.stringType,
                                factory.throwableType),
                            false)))
            .build();
      }
      return DesugarDescription.nothing();
    }
    DexCallSite callSite = instruction.asInvokeDynamic().getCallSite();
    if (isTypeSwitchCallSite(callSite, factory)) {
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (position,
                  freshLocalProvider,
                  localStackAllocator,
                  desugaringInfo,
                  eventConsumer,
                  theContext,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) ->
                  genSwitchMethod(
                      localStackAllocator,
                      eventConsumer,
                      theContext,
                      methodProcessingContext,
                      cfInstructions ->
                          generateTypeSwitchLoadArguments(cfInstructions, callSite, context)))
          .build();
    }
    if (isEnumSwitchCallSite(callSite, factory)) {
      DexType enumType = callSite.methodProto.getParameter(0);
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (position,
                  freshLocalProvider,
                  localStackAllocator,
                  desugaringInfo,
                  eventConsumer,
                  theContext,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) ->
                  genSwitchMethod(
                      localStackAllocator,
                      eventConsumer,
                      theContext,
                      methodProcessingContext,
                      cfInstructions ->
                          generateEnumSwitchLoadArguments(
                              cfInstructions, callSite, context, enumType)))
          .build();
    }
    return DesugarDescription.nothing();
  }

  private List<CfInstruction> genSwitchMethod(
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod theContext,
      MethodProcessingContext methodProcessingContext,
      Consumer<List<CfInstruction>> generator) {
    localStackAllocator.allocateLocalStack(3);
    DexProgramClass clazz =
        appView
            .getSyntheticItems()
            .createClass(
                kinds -> kinds.TYPE_SWITCH_CLASS,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder -> new SwitchHelperGenerator(builder, appView, generator));
    eventConsumer.acceptTypeSwitchClass(clazz, theContext);
    List<CfInstruction> cfInstructions = new ArrayList<>();
    Iterator<DexEncodedMethod> iter = clazz.methods().iterator();
    cfInstructions.add(new CfInvoke(Opcodes.INVOKESTATIC, iter.next().getReference(), false));
    assert !iter.hasNext();
    generateInvokeToDesugaredMethod(
        cfInstructions, methodProcessingContext, theContext, eventConsumer);
    return cfInstructions;
  }

  private void generateInvokeToDesugaredMethod(
      List<CfInstruction> cfInstructions,
      MethodProcessingContext methodProcessingContext,
      ProgramMethod context,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.TYPE_SWITCH_HELPER,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .disableAndroidApiLevelCheck()
                        .setProto(switchHelperProto)
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setCode(
                            methodSig -> {
                              CfCode code =
                                  TypeSwitchMethods.TypeSwitchMethods_typeSwitch(
                                      factory, methodSig);
                              if (appView.options().hasMappingFileSupport()) {
                                return code.getCodeAsInlining(
                                    methodSig, true, context.getReference(), false, factory);
                              }
                              return code;
                            }));
    eventConsumer.acceptTypeSwitchMethod(method, context);
    cfInstructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method.getReference(), false));
  }

  private void generateEnumSwitchLoadArguments(
      List<CfInstruction> cfInstructions,
      DexCallSite callSite,
      ProgramMethod context,
      DexType enumType) {
    generateSwitchLoadArguments(
        cfInstructions,
        callSite,
        bootstrapArg -> {
          if (bootstrapArg.isDexValueType()) {
            cfInstructions.add(new CfConstClass(bootstrapArg.asDexValueType().getValue()));
          } else if (bootstrapArg.isDexValueString()) {
            DexField enumField =
                getEnumField(bootstrapArg.asDexValueString().getValue(), enumType, appView);
            pushEnumField(cfInstructions, enumField);
          } else {
            throw new CompilationError(
                "Invalid bootstrap arg for enum switch " + bootstrapArg, context.getOrigin());
          }
        });
  }

  private void generateTypeSwitchLoadArguments(
      List<CfInstruction> cfInstructions, DexCallSite callSite, ProgramMethod context) {
    generateSwitchLoadArguments(
        cfInstructions,
        callSite,
        bootstrapArg -> {
          if (bootstrapArg.isDexValueType()) {
            cfInstructions.add(new CfConstClass(bootstrapArg.asDexValueType().getValue()));
          } else if (bootstrapArg.isDexValueInt()) {
            cfInstructions.add(
                new CfConstNumber(bootstrapArg.asDexValueInt().getValue(), ValueType.INT));
            cfInstructions.add(
                new CfInvoke(Opcodes.INVOKESTATIC, factory.integerMembers.valueOf, false));
          } else if (bootstrapArg.isDexValueString()) {
            cfInstructions.add(new CfConstString(bootstrapArg.asDexValueString().getValue()));
          } else if (bootstrapArg.isDexValueConstDynamic()) {
            DexField enumField =
                extractEnumField(bootstrapArg.asDexValueConstDynamic(), context, appView);
            pushEnumField(cfInstructions, enumField);
          } else {
            throw new CompilationError(
                "Invalid bootstrap arg for type switch " + bootstrapArg, context.getOrigin());
          }
        });
  }

  private void pushEnumField(List<CfInstruction> cfInstructions, DexField enumField) {
    if (enumField == null) {
      // Extremely rare case where the compilation is invalid, the case is unreachable.
      cfInstructions.add(new CfConstNull());
    } else {
      cfInstructions.add(new CfStaticFieldRead(enumField));
    }
  }

  private void generateSwitchLoadArguments(
      List<CfInstruction> cfInstructions, DexCallSite callSite, Consumer<DexValue> adder) {
    // We need to call the method with the bootstrap args as parameters.
    // We need to convert the bootstrap args into a list of cf instructions.
    // The object and the int are already pushed on stack, we simply need to push the extra array.
    cfInstructions.add(new CfConstNumber(callSite.bootstrapArgs.size(), ValueType.INT));
    cfInstructions.add(new CfNewArray(factory.objectArrayType));
    for (int i = 0; i < callSite.bootstrapArgs.size(); i++) {
      DexValue bootstrapArg = callSite.bootstrapArgs.get(i);
      cfInstructions.add(new CfStackInstruction(Opcode.Dup));
      cfInstructions.add(new CfConstNumber(i, ValueType.INT));
      adder.accept(bootstrapArg);
      cfInstructions.add(new CfArrayStore(MemberType.OBJECT));
    }
  }
}
