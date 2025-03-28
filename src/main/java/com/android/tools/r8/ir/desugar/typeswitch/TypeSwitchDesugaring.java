// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import static com.android.tools.r8.ir.desugar.constantdynamic.LibraryConstantDynamic.dispatchEnumDescConstantDynamic;
import static com.android.tools.r8.ir.desugar.constantdynamic.LibraryConstantDynamic.extractBoxedBooleanConstantDynamic;
import static com.android.tools.r8.ir.desugar.constantdynamic.LibraryConstantDynamic.extractPrimitiveClassConstantDynamic;
import static com.android.tools.r8.ir.desugar.constantdynamic.LibraryConstantDynamic.isBoxedBooleanConstantDynamic;
import static com.android.tools.r8.ir.desugar.constantdynamic.LibraryConstantDynamic.isEnumDescConstantDynamic;
import static com.android.tools.r8.ir.desugar.constantdynamic.LibraryConstantDynamic.isPrimitiveClassConstantDynamic;
import static com.android.tools.r8.ir.desugar.typeswitch.TypeSwitchDesugaringHelper.isEnumSwitchCallSite;
import static com.android.tools.r8.ir.desugar.typeswitch.TypeSwitchDesugaringHelper.isTypeSwitchCallSite;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicReference;
import com.android.tools.r8.ir.desugar.typeswitch.SwitchHelperGenerator.Scanner;
import com.android.tools.r8.ir.synthetic.TypeSwitchSyntheticCfCodeProvider.Dispatcher;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

public class TypeSwitchDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;

  // private final DexProto switchHelperProto;
  private final DexType matchException;
  private final DexMethod matchExceptionInit;
  private final DexItemFactory factory;

  public TypeSwitchDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
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
                      callSite,
                      eventConsumer,
                      theContext,
                      methodProcessingContext,
                      typeScanner(),
                      typeDispatcher(context)))
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
                      callSite,
                      eventConsumer,
                      theContext,
                      methodProcessingContext,
                      enumScanner(),
                      enumDispatcher(context, enumType)))
          .build();
    }
    return DesugarDescription.nothing();
  }

  private List<CfInstruction> genSwitchMethod(
      DexCallSite dexCallSite,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      Scanner scanner,
      Dispatcher dispatcher) {
    SwitchHelperGenerator gen = new SwitchHelperGenerator(appView, dexCallSite);
    DexProgramClass clazz =
        appView
            .getSyntheticItems()
            .createClass(
                kinds -> kinds.TYPE_SWITCH_CLASS,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    gen.build(
                        builder,
                        scanner,
                        dispatcher,
                        context,
                        eventConsumer,
                        methodProcessingContext));
    eventConsumer.acceptTypeSwitchClass(clazz, context);
    assert gen.getDispatchMethod() != null;
    return ImmutableList.of(new CfInvoke(Opcodes.INVOKESTATIC, gen.getDispatchMethod(), false));
  }

  private Dispatcher enumDispatcher(ProgramMethod context, DexType enumType) {
    return (dexValue,
        dexTypeConsumer,
        intValueConsumer,
        dexStringConsumer,
        enumConsumer,
        booleanConsumer,
        numberConsumer) -> {
      if (dexValue.isDexValueType()) {
        dexTypeConsumer.accept(dexValue.asDexValueType().getValue());
      } else if (dexValue.isDexValueString()) {
        enumConsumer.accept(enumType, dexValue.asDexValueString().getValue());
      } else {
        throw new CompilationError(
            "Invalid bootstrap arg for enum switch " + dexValue, context.getOrigin());
      }
    };
  }

  private Scanner enumScanner() {
    return (dexValue, intEqCheck, enumCase) -> {
      if (dexValue.isDexValueString()) {
        enumCase.run();
      }
    };
  }

  private Dispatcher typeDispatcher(ProgramMethod context) {
    return (dexValue,
        dexTypeConsumer,
        intValueConsumer,
        dexStringConsumer,
        enumConsumer,
        booleanConsumer,
        numberConsumer) -> {
      if (dexValue.isDexValueType()) {
        dexTypeConsumer.accept(dexValue.asDexValueType().getValue());
      } else if (dexValue.isDexValueInt()) {
        intValueConsumer.accept(dexValue.asDexValueInt().getValue());
      } else if (dexValue.isDexValueString()) {
        dexStringConsumer.accept(dexValue.asDexValueString().getValue());
      } else if (dexValue.isDexValueConstDynamic()) {
        ConstantDynamicReference constDynamic = dexValue.asDexValueConstDynamic().getValue();
        if (isBoxedBooleanConstantDynamic(constDynamic, factory)) {
          booleanConsumer.accept(
              extractBoxedBooleanConstantDynamic(constDynamic, factory, context));
        } else if (isEnumDescConstantDynamic(constDynamic, factory)) {
          dispatchEnumDescConstantDynamic(constDynamic, factory, context, enumConsumer);
        } else if (isPrimitiveClassConstantDynamic(constDynamic, factory)) {
          dexTypeConsumer.accept(
              extractPrimitiveClassConstantDynamic(constDynamic, factory, context));
        } else {
          throw new CompilationError(
              "Invalid constant dynamic for type switch" + dexValue, context.getOrigin());
        }
      } else if (dexValue.isDexValueNumber()) {
        assert dexValue.isDexValueDouble()
            || dexValue.isDexValueFloat()
            || dexValue.isDexValueLong();
        numberConsumer.accept(dexValue.asDexValueNumber());
      } else {
        throw new CompilationError(
            "Invalid bootstrap arg for type switch " + dexValue, context.getOrigin());
      }
    };
  }

  private Scanner typeScanner() {
    return (dexValue, intEqCheck, enumCase) -> {
      if (dexValue.isDexValueInt()) {
        intEqCheck.run();
      } else if (dexValue.isDexValueConstDynamic()) {
        enumCase.run();
      }
    };
  }
}
