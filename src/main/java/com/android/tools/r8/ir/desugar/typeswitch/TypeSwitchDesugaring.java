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
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class TypeSwitchDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;

  private final DexString typeSwitch;
  private final DexMethod typeSwitchMethod;
  private final DexProto typeSwitchProto;
  private final DexProto typeSwitchHelperProto;

  public TypeSwitchDesugaring(AppView<?> appView) {
    this.appView = appView;
    DexItemFactory factory = appView.dexItemFactory();
    typeSwitchProto = factory.createProto(factory.intType, factory.objectType, factory.intType);
    DexType switchBootstrap = factory.createType("Ljava/lang/runtime/SwitchBootstraps;");
    typeSwitch = factory.createString("typeSwitch");
    typeSwitchMethod =
        factory.createMethod(
            switchBootstrap,
            factory.createProto(
                factory.callSiteType,
                factory.methodHandlesLookupType,
                factory.stringType,
                factory.methodTypeType,
                factory.objectArrayType),
            typeSwitch);
    typeSwitchHelperProto =
        factory.createProto(
            factory.intType, factory.objectType, factory.intType, factory.objectArrayType);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvokeDynamic()) {
      return DesugarDescription.nothing();
    }
    DexCallSite callSite = instruction.asInvokeDynamic().getCallSite();
    if (!(callSite.methodName.isIdenticalTo(typeSwitch)
        && callSite.methodProto.isIdenticalTo(typeSwitchProto)
        && callSite.bootstrapMethod.member.isDexMethod()
        && callSite.bootstrapMethod.member.asDexMethod().isIdenticalTo(typeSwitchMethod))) {
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
              List<CfInstruction> cfInstructions = generateLoadArguments(callSite);
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
                                      appView.dexItemFactory(), methodSig);
                              if (appView.options().hasMappingFileSupport()) {
                                return code.getCodeAsInlining(
                                    methodSig,
                                    true,
                                    context.getReference(),
                                    false,
                                    appView.dexItemFactory());
                              }
                              return code;
                            }));
    eventConsumer.acceptTypeSwitchMethod(method, context);
    cfInstructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method.getReference(), false));
  }

  private List<CfInstruction> generateLoadArguments(DexCallSite callSite) {
    // We need to call the method with the bootstrap args as parameters.
    // We need to convert the bootstrap args into a list of cf instructions.
    // The object and the int are already pushed on stack, we simply need to push the extra array.
    List<CfInstruction> cfInstructions = new ArrayList<>();
    cfInstructions.add(new CfConstNumber(callSite.bootstrapArgs.size(), ValueType.INT));
    cfInstructions.add(new CfNewArray(appView.dexItemFactory().objectArrayType));
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
            new CfInvoke(
                Opcodes.INVOKESTATIC, appView.dexItemFactory().integerMembers.valueOf, false));
      } else if (bootstrapArg.isDexValueString()) {
        cfInstructions.add(new CfConstString(bootstrapArg.asDexValueString().getValue()));
      } else {
        assert bootstrapArg.isDexValueConstDynamic();
        throw new Unreachable("TODO(b/336510513): Enum descriptor should be implemented");
      }
      cfInstructions.add(new CfArrayStore(MemberType.OBJECT));
    }
    return cfInstructions;
  }
}
