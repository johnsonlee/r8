// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import java.util.Map;

public class ReflectiveInstrumentation {

  private final AppView<AppInfo> appView;
  private final PrimaryD8L8IRConverter converter;
  private final DexItemFactory dexItemFactory;
  private final Timing timing;
  private final ReflectiveReferences reflectiveReferences;

  public ReflectiveInstrumentation(
      AppView<AppInfo> appView, PrimaryD8L8IRConverter converter, Timing timing) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.reflectiveReferences = new ReflectiveReferences(dexItemFactory);
    this.converter = converter;
    this.timing = timing;
  }

  public void updateReflectiveReceiver(String customReflectiveReceiverDescriptor) {
    ProgramMethod getReceiver =
        appView.definitionFor(reflectiveReferences.getReceiverMethod).asProgramMethod();
    IRCode code = getReceiver.buildIR(appView);
    assert code.streamInstructions().count() == 3;
    DexType replacementType = dexItemFactory.createType(customReflectiveReceiverDescriptor);
    IRCodeInstructionListIterator instructionListIterator = code.instructionListIterator();
    instructionListIterator.next();
    NewInstance newInstanceReplacement =
        NewInstance.builder()
            .setType(replacementType)
            .setFreshOutValue(code, replacementType.toNonNullTypeElement(appView))
            .build();
    Value newInstanceOutValue = newInstanceReplacement.outValue();
    instructionListIterator.replaceCurrentInstruction(newInstanceReplacement);
    instructionListIterator.next();
    DexMethod method = dexItemFactory.createInstanceInitializer(replacementType);
    InvokeDirect invokeDirect =
        InvokeDirect.builder().setMethod(method).setArguments(newInstanceOutValue).build();
    instructionListIterator.replaceCurrentInstruction(invokeDirect);
    instructionListIterator.next();
    Return newReturn = Return.builder().setReturnValue(newInstanceOutValue).build();
    instructionListIterator.replaceCurrentInstruction(newReturn);
    converter.removeDeadCodeAndFinalizeIR(code, OptimizationFeedback.getIgnoreFeedback(), timing);
  }

  // TODO(b/394013779): Do this in parallel.
  public void instrumentClasses() {
    Map<DexMethod, DexMethod> instrumentedMethodsAndTargets =
        new InstrumentedReflectiveMethodList(dexItemFactory).getInstrumentedMethodsAndTargets();
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
                if (!instruction.isInvokeVirtual() && !instruction.isInvokeStatic()) {
                  continue;
                }
                InvokeMethod invoke = instruction.asInvokeMethod();
                DexMethod invokedMethod = invoke.getInvokedMethod();

                DexMethod toInstrumentCallTo = instrumentedMethodsAndTargets.get(invokedMethod);
                if (toInstrumentCallTo != null) {
                  insertCallToMethod(
                      toInstrumentCallTo, irCode, blockIterator, instructionIterator, invoke);
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

  private void insertCallToMethod(
      DexMethod method,
      IRCode code,
      BasicBlockIterator blockIterator,
      BasicBlockInstructionListIterator instructionIterator,
      InvokeMethod invoke) {
    InvokeStatic invokeStatic =
        InvokeStatic.builder()
            .setMethod(method)
            .setArguments(invoke.inValues())
            // Same position so that the stack trace has the correct line number.
            .setPosition(invoke.getPosition())
            .build();
    instructionIterator.previous();
    instructionIterator.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
        code, blockIterator, ImmutableList.of(invokeStatic), appView.options());
    if (instructionIterator.hasNext()) {
      instructionIterator.next();
    }
  }
}
