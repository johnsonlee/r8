// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import static com.android.tools.r8.ir.synthetic.TypeSwitchSyntheticCfCodeProvider.allowsInlinedIntegerEquality;

import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.synthetic.TypeSwitchSyntheticCfCodeProvider;
import com.android.tools.r8.ir.synthetic.TypeSwitchSyntheticCfCodeProvider.Dispatcher;
import com.android.tools.r8.synthesis.SyntheticItems.SyntheticKindSelector;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class SwitchHelperGenerator {

  private final AppView<?> appView;
  private final DexCallSite dexCallSite;
  private DexMethod dispatchMethod;
  private DexMethod intEq;
  private DexField enumCacheField;
  private int enumCases = 0;
  private final Map<DexType, DexMethod> enumEqMethods = new IdentityHashMap<>();

  SwitchHelperGenerator(AppView<?> appView, DexCallSite dexCallSite) {
    this.appView = appView;
    this.dexCallSite = dexCallSite;
  }

  public void build(
      SyntheticProgramClassBuilder builder,
      Scanner scanner,
      Dispatcher dispatcher,
      ProgramMethod context,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory factory = appView.dexItemFactory();
    scanArguments(
        dexCallSite.bootstrapArgs, scanner, context, eventConsumer, methodProcessingContext);
    DexEncodedMethod clinitMethod = null;
    if (enumCases > 0) {
      enumCacheField =
          factory.createField(
              builder.getType(), factory.objectArrayType, factory.createString("enumCache"));
      synthesizeStaticField(builder);
      clinitMethod = synthesizeClinit(enumCacheField);
    }
    dispatchMethod =
        factory.createMethod(
            builder.getType(), dexCallSite.methodProto, factory.createString("switchDispatch"));
    DexEncodedMethod dispatchMethod =
        synthesizeDispatchMethod(builder, dispatcher, dexCallSite, appView);
    List<DexEncodedMethod> directMethods = new ArrayList<>();
    directMethods.add(dispatchMethod);
    if (clinitMethod != null) {
      directMethods.add(clinitMethod);
    }
    builder.setDirectMethods(directMethods);
  }

  public DexMethod getDispatchMethod() {
    return dispatchMethod;
  }

  @FunctionalInterface
  public interface Scanner {

    void scan(DexValue dexValue, Runnable intEqCheck, Consumer<DexType> enumCase);
  }

  private void scanArguments(
      List<DexValue> bootstrapArgs,
      Scanner scanner,
      ProgramMethod context,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    for (DexValue bootstrapArg : bootstrapArgs) {
      scanner.scan(
          bootstrapArg,
          () -> {
            DexItemFactory factory = appView.dexItemFactory();
            DexType arg0Type = dexCallSite.methodProto.getParameter(0);
            if (arg0Type.isPrimitiveType() || allowsInlinedIntegerEquality(arg0Type, factory)) {
              return;
            }
            intEq = generateIntEqMethod(context, eventConsumer, methodProcessingContext);
          },
          type -> {
            enumCases++;
            enumEqMethods.computeIfAbsent(
                type,
                t -> generateEnumEqMethod(t, context, eventConsumer, methodProcessingContext));
          });
    }
  }

  private DexMethod generateEnumEqMethod(
      DexType enumType,
      ProgramMethod context,
      TypeSwitchDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory factory = appView.dexItemFactory();
    DexProto proto =
        factory.createProto(
            factory.booleanType,
            factory.objectType,
            factory.objectArrayType,
            factory.intType,
            factory.stringType);
    return generateMethod(
        context,
        eventConsumer,
        methodProcessingContext,
        proto,
        methodSig -> {
          CfCode cfCode = TypeSwitchMethods.TypeSwitchMethods_switchEnumEq(factory, methodSig);
          List<CfInstruction> newInstructions =
              ListUtils.map(
                  cfCode.getInstructions(),
                  i -> {
                    if (i.isInvokeStatic()) {
                      CfInvoke invoke = i.asInvoke();
                      if (invoke.getMethod().getName().isIdenticalTo(factory.valueOfMethodName)) {
                        DexMethod newMethod =
                            factory.createMethod(
                                enumType,
                                factory.createProto(enumType, factory.stringType),
                                factory.valueOfMethodName);
                        return new CfInvoke(invoke.getOpcode(), newMethod, invoke.isInterface());
                      }
                    }
                    return i;
                  });
          cfCode.setInstructions(newInstructions);
          return cfCode;
        },
        kinds -> kinds.TYPE_SWITCH_HELPER_ENUM);
  }

  private DexMethod generateIntEqMethod(
      ProgramMethod context,
      TypeSwitchDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    DexItemFactory factory = appView.dexItemFactory();
    DexProto proto = factory.createProto(factory.booleanType, factory.objectType, factory.intType);
    return generateMethod(
        context,
        eventConsumer,
        methodProcessingContext,
        proto,
        methodSig -> TypeSwitchMethods.TypeSwitchMethods_switchIntEq(factory, methodSig),
        kinds -> kinds.TYPE_SWITCH_HELPER_INT);
  }

  private DexMethod generateMethod(
      ProgramMethod context,
      TypeSwitchDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext,
      DexProto proto,
      Function<DexMethod, CfCode> cfCodeGen,
      SyntheticKindSelector kindSelector) {
    DexItemFactory factory = appView.dexItemFactory();
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kindSelector,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .disableAndroidApiLevelCheck()
                        .setProto(proto)
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setCode(
                            methodSig -> {
                              CfCode code = cfCodeGen.apply(methodSig);
                              if (appView.options().hasMappingFileSupport()) {
                                return code.getCodeAsInlining(
                                    methodSig, true, context.getReference(), false, factory);
                              }
                              return code;
                            }));
    eventConsumer.acceptTypeSwitchMethod(method, context);
    return method.getReference();
  }

  private void synthesizeStaticField(SyntheticProgramClassBuilder builder) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.syntheticBuilder()
                .setField(enumCacheField)
                .setAccessFlags(FieldAccessFlags.createPublicStaticSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
  }

  private DexEncodedMethod synthesizeClinit(DexField enumCacheField) {
    DexItemFactory factory = appView.dexItemFactory();
    DexMethod clinitMethod = factory.createClinitMethod(enumCacheField.getHolderType());
    return DexEncodedMethod.syntheticBuilder()
        .setMethod(clinitMethod)
        .setAccessFlags(MethodAccessFlags.createForClassInitializer())
        .setCode(
            new CfCode(enumCacheField.getHolderType(), 2, 1, instructionsForClinit(enumCacheField)))
        .disableAndroidApiLevelCheck()
        .build();
  }

  private List<CfInstruction> instructionsForClinit(DexField enumCacheField) {
    DexItemFactory factory = appView.dexItemFactory();
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfConstNumber(enumCases, ValueType.INT));
    instructions.add(new CfNewArray(factory.objectArrayType));
    instructions.add(new CfStaticFieldWrite(enumCacheField));
    instructions.add(new CfReturnVoid());
    return instructions;
  }

  private DexEncodedMethod synthesizeDispatchMethod(
      SyntheticProgramClassBuilder builder,
      Dispatcher dispatcher,
      DexCallSite dexCallSite,
      AppView<?> appView) {
    return DexEncodedMethod.syntheticBuilder()
        .setMethod(dispatchMethod)
        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
        .setCode(
            new TypeSwitchSyntheticCfCodeProvider(
                    appView,
                    builder.getType(),
                    dexCallSite.methodProto.getParameter(0),
                    dexCallSite.bootstrapArgs,
                    dispatcher,
                    intEq,
                    enumEqMethods,
                    enumCacheField)
                .generateCfCode())
        .disableAndroidApiLevelCheck()
        .build();
  }
}
