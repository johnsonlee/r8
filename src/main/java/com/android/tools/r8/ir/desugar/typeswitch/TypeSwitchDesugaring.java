// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstClass;
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
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueConstDynamic;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class TypeSwitchDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;

  private final DexMethod typeSwitchMethod;
  private final DexProto typeSwitchProto;
  private final DexProto typeSwitchHelperProto;
  private final DexMethod enumDescMethod;
  private final DexMethod classDescMethod;
  private final DexType matchException;
  private final DexMethod matchExceptionInit;
  private final DexItemFactory factory;

  public TypeSwitchDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    DexType switchBootstrap = factory.createType("Ljava/lang/runtime/SwitchBootstraps;");
    typeSwitchMethod =
        factory.createMethod(
            switchBootstrap,
            factory.createProto(
                factory.callSiteType,
                factory.methodHandlesLookupType,
                factory.stringType,
                factory.methodTypeType,
                factory.objectArrayType),
            factory.createString("typeSwitch"));
    typeSwitchProto = factory.createProto(factory.intType, factory.objectType, factory.intType);
    typeSwitchHelperProto =
        factory.createProto(
            factory.intType, factory.objectType, factory.intType, factory.objectArrayType);
    enumDescMethod =
        factory.createMethod(
            factory.enumDescType,
            factory.createProto(factory.enumDescType, factory.classDescType, factory.stringType),
            "of");
    classDescMethod =
        factory.createMethod(
            factory.classDescType,
            factory.createProto(factory.classDescType, factory.stringType),
            "of");
    matchException = factory.createType("Ljava/lang/MatchException;");
    matchExceptionInit =
        factory.createInstanceInitializer(
            matchException, factory.stringType, factory.throwableType);
  }

  private boolean methodHandleIsInvokeStaticTo(DexValue dexValue, DexMethod method) {
    if (!dexValue.isDexValueMethodHandle()) {
      return false;
    }
    return methodHandleIsInvokeStaticTo(dexValue.asDexValueMethodHandle().getValue(), method);
  }

  private boolean methodHandleIsInvokeStaticTo(DexMethodHandle methodHandle, DexMethod method) {
    return methodHandle.type.isInvokeStatic() && methodHandle.asMethod().isIdenticalTo(method);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvokeDynamic()) {
      // We need to replace the new MatchException with RuntimeException.
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
    if (!(callSite.methodName.isIdenticalTo(typeSwitchMethod.getName())
        && callSite.methodProto.isIdenticalTo(typeSwitchProto)
        && methodHandleIsInvokeStaticTo(callSite.bootstrapMethod, typeSwitchMethod))) {
      return DesugarDescription.nothing();
    }
    // Call the desugared method.
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
                dexItemFactory) -> {
              // We add on stack (2) array, (3) dupped array, (4) index, (5) value.
              localStackAllocator.allocateLocalStack(4);
              List<CfInstruction> cfInstructions = generateLoadArguments(callSite, context);
              generateInvokeToDesugaredMethod(
                  methodProcessingContext, cfInstructions, theContext, eventConsumer);
              return cfInstructions;
            })
        .build();
  }

  private void generateInvokeToDesugaredMethod(
      MethodProcessingContext methodProcessingContext,
      List<CfInstruction> cfInstructions,
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
                        .setProto(typeSwitchHelperProto)
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

  private List<CfInstruction> generateLoadArguments(DexCallSite callSite, ProgramMethod context) {
    // We need to call the method with the bootstrap args as parameters.
    // We need to convert the bootstrap args into a list of cf instructions.
    // The object and the int are already pushed on stack, we simply need to push the extra array.
    List<CfInstruction> cfInstructions = new ArrayList<>();
    cfInstructions.add(new CfConstNumber(callSite.bootstrapArgs.size(), ValueType.INT));
    cfInstructions.add(new CfNewArray(factory.objectArrayType));
    for (int i = 0; i < callSite.bootstrapArgs.size(); i++) {
      DexValue bootstrapArg = callSite.bootstrapArgs.get(i);
      cfInstructions.add(new CfStackInstruction(Opcode.Dup));
      cfInstructions.add(new CfConstNumber(i, ValueType.INT));
      if (bootstrapArg.isDexValueType()) {
        cfInstructions.add(new CfConstClass(bootstrapArg.asDexValueType().getValue()));
      } else if (bootstrapArg.isDexValueInt()) {
        cfInstructions.add(
            new CfConstNumber(bootstrapArg.asDexValueInt().getValue(), ValueType.INT));
        cfInstructions.add(
            new CfInvoke(Opcodes.INVOKESTATIC, factory.integerMembers.valueOf, false));
      } else if (bootstrapArg.isDexValueString()) {
        cfInstructions.add(new CfConstString(bootstrapArg.asDexValueString().getValue()));
      } else {
        assert bootstrapArg.isDexValueConstDynamic();
        DexField enumField = extractEnumField(bootstrapArg.asDexValueConstDynamic(), context);
        cfInstructions.add(new CfStaticFieldRead(enumField));
      }
      cfInstructions.add(new CfArrayStore(MemberType.OBJECT));
    }
    return cfInstructions;
  }

  private CompilationError throwEnumFieldConstantDynamic(String msg, ProgramMethod context) {
    throw new CompilationError(
        "Unexpected ConstantDynamic in TypeSwitch: " + msg, context.getOrigin());
  }

  private DexField extractEnumField(
      DexValueConstDynamic dexValueConstDynamic, ProgramMethod context) {
    ConstantDynamicReference enumCstDynamic = dexValueConstDynamic.getValue();
    DexMethod bootstrapMethod = factory.constantDynamicBootstrapMethod;
    if (!(enumCstDynamic.getType().isIdenticalTo(factory.enumDescType)
        && enumCstDynamic.getName().isIdenticalTo(bootstrapMethod.getName())
        && enumCstDynamic.getBootstrapMethod().asMethod().isIdenticalTo(bootstrapMethod)
        && enumCstDynamic.getBootstrapMethodArguments().size() == 3
        && methodHandleIsInvokeStaticTo(
            enumCstDynamic.getBootstrapMethodArguments().get(0), enumDescMethod))) {
      throw throwEnumFieldConstantDynamic("Invalid EnumDesc", context);
    }
    DexValue dexValueFieldName = enumCstDynamic.getBootstrapMethodArguments().get(2);
    if (!dexValueFieldName.isDexValueString()) {
      throw throwEnumFieldConstantDynamic("Field name " + dexValueFieldName, context);
    }
    DexString fieldName = dexValueFieldName.asDexValueString().getValue();

    DexValue dexValueClassCstDynamic = enumCstDynamic.getBootstrapMethodArguments().get(1);
    if (!dexValueClassCstDynamic.isDexValueConstDynamic()) {
      throw throwEnumFieldConstantDynamic("Enum class " + dexValueClassCstDynamic, context);
    }
    ConstantDynamicReference classCstDynamic =
        dexValueClassCstDynamic.asDexValueConstDynamic().getValue();
    if (!(classCstDynamic.getType().isIdenticalTo(factory.classDescType)
        && classCstDynamic.getName().isIdenticalTo(bootstrapMethod.getName())
        && classCstDynamic.getBootstrapMethod().asMethod().isIdenticalTo(bootstrapMethod)
        && classCstDynamic.getBootstrapMethodArguments().size() == 2
        && methodHandleIsInvokeStaticTo(
            classCstDynamic.getBootstrapMethodArguments().get(0), classDescMethod))) {
      throw throwEnumFieldConstantDynamic("Class descriptor " + classCstDynamic, context);
    }
    DexValue dexValueClassName = classCstDynamic.getBootstrapMethodArguments().get(1);
    if (!dexValueClassName.isDexValueString()) {
      throw throwEnumFieldConstantDynamic("Class name " + dexValueClassName, context);
    }
    DexString className = dexValueClassName.asDexValueString().getValue();
    DexType enumType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(className.toString()));
    DexClass enumClass = appView.definitionFor(enumType);
    if (enumClass == null) {
      throw throwEnumFieldConstantDynamic("Missing enum class " + enumType, context);
    }
    DexEncodedField dexEncodedField = enumClass.lookupUniqueStaticFieldWithName(fieldName);
    if (dexEncodedField == null) {
      throw throwEnumFieldConstantDynamic(
          "Missing enum field " + fieldName + " in " + enumType, context);
    }
    return dexEncodedField.getReference();
  }
}
