// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodDesugaringMode.LIBRARY_DESUGARING_N_PLUS;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfOpcodeUtils;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.ProgramAdditions;
import com.google.common.collect.Iterables;
import java.util.Set;
import java.util.function.IntConsumer;

public class CfToCfInterfaceMethodRewriter extends InterfaceMethodRewriter
    implements CfInstructionDesugaring {

  // This is used to filter out double desugaring.
  private final Set<CfInstructionDesugaring> precedingDesugaringsForInvoke;
  private final Set<CfInstructionDesugaring> precedingDesugaringsForInvokeDynamic;

  CfToCfInterfaceMethodRewriter(
      AppView<?> appView,
      InterfaceMethodDesugaringMode desugaringMode,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvoke,
      Set<CfInstructionDesugaring> precedingDesugaringsForInvokeDynamic) {
    super(appView, desugaringMode);
    this.precedingDesugaringsForInvoke = precedingDesugaringsForInvoke;
    this.precedingDesugaringsForInvokeDynamic = precedingDesugaringsForInvokeDynamic;
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfInvokeOpcodes(consumer);
  }

  @Override
  public boolean hasPreciseNeedsDesugaring() {
    return false;
  }

  /**
   * If the method is not required to be desugared, scanning is used to upgrade when required the
   * class file version, as well as reporting missing type.
   */
  @Override
  public void prepare(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramAdditions programAdditions) {
    if (desugaringMode == LIBRARY_DESUGARING_N_PLUS) {
      return;
    }
    if (isSyntheticMethodThatShouldNotBeDoubleProcessed(method)) {
      leavingStaticInvokeToInterface(method);
      return;
    }
    CfCode code = method.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isInvokeDynamic()
          && !isAlreadyDesugared(instruction.asInvokeDynamic(), method)) {
        reportInterfaceMethodHandleCallSite(instruction.asInvokeDynamic().getCallSite(), method);
      }
      compute(instruction, method).scan();
    }
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    CfInvoke invoke = instruction.asInvoke();
    // Interface desugaring is only interested in invokes that are not desugared by preceeding
    // desugarings.
    if (invoke != null && !isAlreadyDesugared(invoke, context)) {
      return compute(
          invoke.getMethod(),
          invoke.getInvokeTypePreDesugar(
              appView, context.getDefinition().getCode().getCodeLens(appView), context),
          invoke.isInterface(),
          context);
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription compute(
      DexMethod invokedMethod, InvokeType invokeType, boolean isInterface, ProgramMethod context) {
    RetargetMethodSupplier retargetMethodSupplier =
        getRetargetMethodSupplier(invokedMethod, invokeType, isInterface, context);
    return retargetMethodSupplier != RetargetMethodSupplier.none()
        ? retargetMethodSupplier.toDesugarDescription(context)
        : DesugarDescription.nothing();
  }

  private boolean isAlreadyDesugared(CfInvoke invoke, ProgramMethod context) {
    return Iterables.any(
        precedingDesugaringsForInvoke,
        desugaring -> desugaring.compute(invoke, context).needsDesugaring());
  }

  private boolean isAlreadyDesugared(CfInvokeDynamic invoke, ProgramMethod context) {
    return Iterables.any(
        precedingDesugaringsForInvokeDynamic,
        desugaring -> desugaring.compute(invoke, context).needsDesugaring());
  }

  private void reportInterfaceMethodHandleCallSite(DexCallSite callSite, ProgramMethod context) {
    // Check that static interface methods are not referenced from invoke-custom instructions via
    // method handles.
    reportStaticInterfaceMethodHandle(context, callSite.bootstrapMethod);
    for (DexValue arg : callSite.bootstrapArgs) {
      if (arg.isDexValueMethodHandle()) {
        reportStaticInterfaceMethodHandle(context, arg.asDexValueMethodHandle().value);
      }
    }
  }

  private void reportStaticInterfaceMethodHandle(ProgramMethod context, DexMethodHandle handle) {
    if (handle.type.isInvokeStatic()) {
      DexClass holderClass = appView.definitionFor(handle.asMethod().holder);
      // NOTE: If the class definition is missing we can't check. Let it be handled as any other
      // missing call target.
      if (holderClass == null) {
        warnMissingType(context, handle.asMethod().holder);
      } else if (holderClass.isInterface()) {
        throw new Unimplemented(
            "Desugaring of static interface method handle in `"
                + context.toSourceString()
                + "` is not yet supported.");
      }
    }
  }
}
