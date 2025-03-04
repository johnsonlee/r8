// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import static com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider.EmulateDispatchType.ALL_STATIC;
import static com.android.tools.r8.ir.synthetic.EmulateDispatchSyntheticCfCodeProvider.EmulateDispatchType.AUTO_CLOSEABLE;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.ValueType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;

public class EmulateDispatchSyntheticCfCodeProvider extends SyntheticCfCodeProvider {

  public enum EmulateDispatchType {
    ALL_STATIC,
    AUTO_CLOSEABLE
  }

  private final DexMethod forwardingMethod;
  private final DexMethod interfaceMethod;
  private final EmulateDispatchType dispatchType;
  private final LinkedHashMap<DexType, DexMethod> extraDispatchCases;

  public EmulateDispatchSyntheticCfCodeProvider(
      DexType holder,
      DexMethod forwardingMethod,
      DexMethod interfaceMethod,
      LinkedHashMap<DexType, DexMethod> extraDispatchCases,
      EmulateDispatchType dispatchType,
      AppView<?> appView) {
    super(appView, holder);
    this.forwardingMethod = forwardingMethod;
    this.interfaceMethod = interfaceMethod;
    this.extraDispatchCases = extraDispatchCases;
    this.dispatchType = dispatchType;
  }

  @Override
  public CfCode generateCfCode() {
    DexType receiverType = forwardingMethod.getParameter(0);
    List<CfInstruction> instructions = new ArrayList<>();
    CfLabel[] labels = new CfLabel[extraDispatchCases.size() + 1];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = new CfLabel();
    }
    int nextLabel = 0;

    CfFrame frame =
        CfFrame.builder()
            .appendLocal(FrameType.initialized(receiverType))
            .apply(
                builder -> {
                  for (DexType parameter : interfaceMethod.getParameters()) {
                    builder.appendLocal(FrameType.initialized(parameter));
                  }
                })
            .build();

    instructions.add(new CfLoad(ValueType.fromDexType(receiverType), 0));
    instructions.add(new CfInstanceOf(interfaceMethod.holder));
    instructions.add(new CfIf(IfType.EQ, ValueType.INT, labels[nextLabel]));

    // Branch with library call.
    instructions.add(new CfLoad(ValueType.fromDexType(receiverType), 0));
    instructions.add(new CfCheckCast(interfaceMethod.holder));
    loadExtraParameters(instructions);
    instructions.add(new CfInvoke(Opcodes.INVOKEINTERFACE, interfaceMethod, true));
    addReturn(instructions);

    // SubInterface dispatch (subInterfaces are ordered).
    for (Map.Entry<DexType, DexMethod> dispatch : extraDispatchCases.entrySet()) {
      // Type check basic block.
      instructions.add(labels[nextLabel++]);
      instructions.add(frame);
      instructions.add(new CfLoad(ValueType.fromDexType(receiverType), 0));
      instructions.add(new CfInstanceOf(dispatch.getKey()));
      instructions.add(new CfIf(IfType.EQ, ValueType.INT, labels[nextLabel]));

      // Call basic block.
      instructions.add(new CfLoad(ValueType.fromDexType(receiverType), 0));
      instructions.add(new CfCheckCast(dispatch.getKey()));
      forwardCall(instructions, dispatch.getValue());
      addReturn(instructions);
    }

    // Branch with companion call.
    instructions.add(labels[nextLabel]);
    instructions.add(frame.clone());
    instructions.add(new CfLoad(ValueType.fromDexType(receiverType), 0));
    forwardCall(instructions, forwardingMethod);
    addReturn(instructions);
    return standardCfCodeFromInstructions(instructions);
  }

  private void forwardCall(List<CfInstruction> instructions, DexMethod method) {
    if (dispatchType == ALL_STATIC
        || appView.getSyntheticItems().isSynthetic(method.getHolderType())) {
      loadExtraParameters(instructions);
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method, false));
      return;
    }
    assert dispatchType == AUTO_CLOSEABLE;
    instructions.add(new CfCheckCast(method.holder));
    loadExtraParameters(instructions);
    // The type method.getHolderType() may not resolve if compiled without android library, for
    // example, with the jdk as android.jar.
    if (method
        .getHolderType()
        .isIdenticalTo(appView.dexItemFactory().javaUtilConcurrentExecutorServiceType)) {
      assert appView.definitionFor(method.getHolderType()) == null
          || appView.definitionFor(method.getHolderType()).isInterface();
      instructions.add(new CfInvoke(Opcodes.INVOKEINTERFACE, method, true));
    } else {
      assert appView.definitionFor(method.getHolderType()) == null
          || !appView.definitionFor(method.getHolderType()).isInterface();
      instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, method, false));
    }
  }

  private void loadExtraParameters(List<CfInstruction> instructions) {
    int index = 1;
    for (DexType type : interfaceMethod.proto.parameters.values) {
      instructions.add(new CfLoad(ValueType.fromDexType(type), index++));
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void addReturn(List<CfInstruction> instructions) {
    if (interfaceMethod.getReturnType().isVoidType()) {
      instructions.add(new CfReturnVoid());
    } else {
      instructions.add(new CfReturn(ValueType.fromDexType(interfaceMethod.getReturnType())));
    }
  }
}
