// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;
import org.objectweb.asm.Opcodes;

/** This outlines calls to array clone from within interface methods. See b/342802978 */
public class OutlineArrayCloneFromInterfaceMethodDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;

  public OutlineArrayCloneFromInterfaceMethodDesugaring(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    consumer.accept(Opcodes.INVOKEVIRTUAL);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    // This workaround only applies within default or static interface method.
    if (!context.getHolder().isInterface()) {
      return DesugarDescription.nothing();
    }
    DexEncodedMethod contextDefinition = context.getDefinition();
    if (!contextDefinition.isStatic() && !contextDefinition.isNonPrivateVirtualMethod()) {
      return DesugarDescription.nothing();
    }
    // The target method must be virtual clone on an array type.
    if (!instruction.isInvokeVirtual()) {
      return DesugarDescription.nothing();
    }
    CfInvoke invoke = instruction.asInvoke();
    if (!appView.dexItemFactory().isArrayClone(invoke.getMethod())) {
      return DesugarDescription.nothing();
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position, locals, stack, info, eventConsumer, ctx, pCtx, collection, factory) -> {
              ProgramMethod newProgramMethod =
                  appView
                      .getSyntheticItems()
                      .createMethod(
                          kind -> kind.OBJECT_CLONE_OUTLINE,
                          pCtx.createUniqueContext(),
                          appView,
                          methodBuilder ->
                              methodBuilder
                                  .setProto(
                                      factory.createProto(factory.objectType, factory.objectType))
                                  .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                                  .setCode(this::createCode));
              eventConsumer.acceptInvokeObjectCloneOutliningMethod(newProgramMethod, ctx);
              return createStaticInvoke(newProgramMethod);
            })
        .build();
  }

  private static List<CfInstruction> createStaticInvoke(ProgramMethod newProgramMethod) {
    return Collections.singletonList(
        new CfInvoke(Opcodes.INVOKESTATIC, newProgramMethod.getReference(), false));
  }

  private Code createCode(DexMethod method) {
    return new CfCode(
        method.getHolderType(),
        1,
        1,
        ImmutableList.of(
            new CfLoad(ValueType.OBJECT, 0),
            new CfInvoke(
                Opcodes.INVOKEVIRTUAL, appView.dexItemFactory().objectMembers.clone, false),
            new CfReturn(ValueType.OBJECT)));
  }
}
