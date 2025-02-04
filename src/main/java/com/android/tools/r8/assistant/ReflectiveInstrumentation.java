// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ReflectiveInstrumentation {

  private final AppView<AppInfo> appView;
  private final PrimaryD8L8IRConverter converter;
  private final DexItemFactory dexItemFactory;
  private final Timing timing;
  private final DexType reflectiveOracleType;

  public ReflectiveInstrumentation(
      AppView<AppInfo> appView, PrimaryD8L8IRConverter converter, Timing timing) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.converter = converter;
    this.timing = timing;
    this.reflectiveOracleType =
        dexItemFactory.createType(DescriptorUtils.javaClassToDescriptor(ReflectiveOracle.class));
  }

  // TODO(b/394013779): Do this in parallel.
  public void instrumentClasses() {
    ImmutableMap<DexMethod, DexMethod> instrumentedMethodsAndTargets =
        getInstrumentedMethodsAndTargets();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          method -> method.hasCode() && method.getCode().isDexCode(),
          method -> {
            boolean changed = false;
            // TODO(b/394016252): Consider using UseRegistry for determining that we need IR.
            IRCode irCode = method.buildIR(appView);
            BasicBlockIterator blockIterator = irCode.listIterator();
            while (blockIterator.hasNext()) {
              BasicBlockInstructionListIterator instructionIterator =
                  blockIterator.next().listIterator();
              while (instructionIterator.hasNext()) {
                Instruction instruction = instructionIterator.next();
                if (!instruction.isInvokeVirtual()) {
                  continue;
                }
                InvokeVirtual invokeVirtual = instruction.asInvokeVirtual();
                DexMethod invokedMethod = invokeVirtual.getInvokedMethod();
                DexMethod toInstrumentCallTo = instrumentedMethodsAndTargets.get(invokedMethod);
                if (toInstrumentCallTo != null) {
                  insertCallToMethod(
                      toInstrumentCallTo,
                      irCode,
                      blockIterator,
                      instructionIterator,
                      invokeVirtual);
                  changed = true;
                }
              }
            }
            if (changed) {
              converter.removeDeadCodeAndFinalizeIR(
                  irCode, OptimizationFeedback.getIgnoreFeedback(), timing);
            }
          });
    }
  }

  private ImmutableMap<DexMethod, DexMethod> getInstrumentedMethodsAndTargets() {
    return ImmutableMap.of(
        dexItemFactory.classMethods.newInstance,
        getMethodReferenceWithClassParameter("onClassNewInstance"),
        dexItemFactory.classMethods.getDeclaredMethod,
        getMethodReferenceWithClassMethodNameAndParameters("onClassGetDeclaredMethod"));
  }

  private DexMethod getMethodReferenceWithClassParameter(String name) {
    return dexItemFactory.createMethod(
        reflectiveOracleType,
        dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.classType),
        name);
  }

  private DexMethod getMethodReferenceWithClassMethodNameAndParameters(String name) {
    return dexItemFactory.createMethod(
        reflectiveOracleType,
        dexItemFactory.createProto(
            dexItemFactory.voidType,
            dexItemFactory.classType,
            dexItemFactory.stringType,
            dexItemFactory.classArrayType),
        name);
  }

  private void insertCallToMethod(
      DexMethod method,
      IRCode code,
      BasicBlockIterator blockIterator,
      BasicBlockInstructionListIterator instructionIterator,
      InvokeVirtual invoke) {
    InvokeStatic invokeStatic =
        InvokeStatic.builder()
            .setMethod(method)
            .setArguments(invoke.inValues())
            // Same position so that the stack trace has the correct line number.
            .setPosition(invoke)
            .build();
    instructionIterator.previous();
    instructionIterator.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
        code, blockIterator, ImmutableList.of(invokeStatic), appView.options());
    if (instructionIterator.hasNext()) {
      instructionIterator.next();
    }
  }
}
