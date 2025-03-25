// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.icce;

import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfOpcodeUtils;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.FailedResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.DesugarDescription.ScanCallback;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.MethodSynthesizerConsumer;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.UtilityMethodForCodeOptimizations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.IntConsumer;

public class AlwaysThrowingInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public AlwaysThrowingInstructionDesugaring(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  @Override
  public void acceptRelevantAsmOpcodes(IntConsumer consumer) {
    CfOpcodeUtils.acceptCfFieldInstructionOpcodes(consumer);
    CfOpcodeUtils.acceptCfInvokeOpcodes(consumer);
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      DexField field = fieldInstruction.getField();
      FieldResolutionResult resolutionResult = appView.appInfo().resolveField(field, context);
      if (shouldRewriteFieldAccessToThrow(fieldInstruction, resolutionResult)) {
        return computeFieldInstructionAsThrowRewrite(appView, fieldInstruction, resolutionResult);
      }
    } else if (instruction.isInvoke()) {
      CfInvoke invoke = instruction.asInvoke();
      DexMethod invokedMethod = invoke.getMethod();
      InvokeType invokeType =
          invoke.getInvokeTypePreDesugar(
              appView, context.getDefinition().getCode().getCodeLens(appView), context);
      boolean isInterface = invoke.isInterface();
      MethodResolutionResult resolutionResult =
          appView.appInfo().resolveMethodLegacy(invokedMethod, isInterface);
      if (shouldRewriteInvokeToThrow(invoke, resolutionResult)) {
        return computeInvokeAsThrowRewrite(appView, invokedMethod, invokeType, resolutionResult);
      }
    }
    return DesugarDescription.nothing();
  }

  private boolean shouldRewriteFieldAccessToThrow(
      CfFieldInstruction fieldInstruction, FieldResolutionResult resolutionResult) {
    DexEncodedField resolvedField = resolutionResult.getResolvedField();
    return resolvedField != null
        && resolvedField.isStatic() != fieldInstruction.isStaticFieldInstruction();
  }

  public static DesugarDescription computeFieldInstructionAsThrowRewrite(
      AppView<?> appView,
      CfFieldInstruction fieldInstruction,
      FieldResolutionResult resolutionResult) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                getThrowInstructions(
                    appView,
                    fieldInstruction,
                    localStackAllocator,
                    eventConsumer,
                    context,
                    methodProcessingContext,
                    getMethodSynthesizerForThrowing(fieldInstruction, resolutionResult)))
        .build();
  }

  private static MethodSynthesizerConsumer getMethodSynthesizerForThrowing(
      CfFieldInstruction fieldInstruction, FieldResolutionResult resolutionResult) {
    assert resolutionResult.isSingleFieldResolutionResult();
    assert resolutionResult.getResolvedField().isStatic()
        != fieldInstruction.isStaticFieldInstruction();
    return UtilityMethodsForCodeOptimizations::synthesizeThrowIncompatibleClassChangeErrorMethod;
  }

  private boolean shouldRewriteInvokeToThrow(
      CfInvoke invoke, MethodResolutionResult resolutionResult) {
    if (resolutionResult.isArrayCloneMethodResult()
        || resolutionResult.isMultiMethodResolutionResult()) {
      return false;
    }
    if (resolutionResult.isFailedResolution()) {
      // For now don't materialize NSMEs from failed resolutions.
      return resolutionResult.asFailedResolution().hasMethodsCausingError();
    }
    assert resolutionResult.isSingleResolution();
    return resolutionResult.getResolvedMethod().isStatic() != invoke.isInvokeStatic();
  }

  public static DesugarDescription computeInvokeAsThrowRewrite(
      AppView<?> appView,
      DexMethod invokedMethod,
      InvokeType invokeType,
      MethodResolutionResult resolutionResult) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (position,
                freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                getThrowInstructionsForInvoke(
                    appView,
                    invokedMethod,
                    invokeType,
                    localStackAllocator,
                    eventConsumer,
                    methodProcessingContext,
                    getMethodSynthesizerForThrowing(
                        appView, invokeType, resolutionResult, context)))
        .build();
  }

  public static DesugarDescription computeInvokeAsThrowNSMERewrite(
      AppView<?> appView,
      DexMethod invokedMethod,
      InvokeType invokeType,
      ScanCallback scanCallback) {
    DesugarDescription.Builder builder =
        DesugarDescription.builder()
            .setDesugarRewrite(
                (position,
                    freshLocalProvider,
                    localStackAllocator,
                    desugaringInfo,
                    eventConsumer,
                    context,
                    methodProcessingContext,
                    desugaringCollection,
                    dexItemFactory) ->
                    getThrowInstructionsForInvoke(
                        appView,
                        invokedMethod,
                        invokeType,
                        localStackAllocator,
                        eventConsumer,
                        methodProcessingContext,
                        UtilityMethodsForCodeOptimizations
                            ::synthesizeThrowNoSuchMethodErrorMethod));
    builder.addScanEffect(scanCallback);
    return builder.build();
  }

  private static Collection<CfInstruction> getThrowInstructions(
      AppView<?> appView,
      CfInstruction instruction,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext,
      MethodSynthesizerConsumer methodSynthesizerConsumer) {
    if (methodSynthesizerConsumer == null) {
      assert false;
      return null;
    }

    UtilityMethodForCodeOptimizations throwMethod =
        methodSynthesizerConsumer.synthesizeMethod(appView, eventConsumer, methodProcessingContext);
    ProgramMethod throwProgramMethod = throwMethod.uncheckedGetMethod();

    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      DexType fieldType = fieldInstruction.getField().getType();
      // Replace the field access by a call to the throwing helper.
      ArrayList<CfInstruction> replacement = new ArrayList<>();
      // Pop value.
      if (fieldInstruction.isFieldPut()) {
        replacement.add(
            new CfStackInstruction(
                fieldType.isWideType()
                    ? CfStackInstruction.Opcode.Pop2
                    : CfStackInstruction.Opcode.Pop));
      }
      // Pop receiver.
      if (fieldInstruction.isInstanceFieldInstruction()) {
        replacement.add(new CfStackInstruction(CfStackInstruction.Opcode.Pop));
      }
      // Call throw method and pop exception.
      CfInvoke throwInvoke =
          new CfInvoke(
              org.objectweb.asm.Opcodes.INVOKESTATIC, throwProgramMethod.getReference(), false);
      assert throwInvoke.getMethod().getReturnType().isClassType();
      replacement.add(throwInvoke);
      replacement.add(new CfStackInstruction(CfStackInstruction.Opcode.Pop));
      // Push default field read value.
      if (fieldInstruction.isFieldGet()) {
        replacement.add(
            fieldType.isPrimitiveType()
                ? new CfConstNumber(0, ValueType.fromDexType(fieldType))
                : new CfConstNull());
      }
      return replacement;
    }

    assert instruction.isInvoke();
    CfInvoke invoke = instruction.asInvoke();
    return internalGetThrowInstructionsForInvoke(
        invoke.getMethod(),
        invoke.getInvokeTypePreDesugar(
            appView, method.getDefinition().getCode().getCodeLens(appView), method),
        localStackAllocator,
        throwProgramMethod);
  }

  private static Collection<CfInstruction> getThrowInstructionsForInvoke(
      AppView<?> appView,
      DexMethod invokedMethod,
      InvokeType invokeType,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext,
      MethodSynthesizerConsumer methodSynthesizerConsumer) {
    if (methodSynthesizerConsumer == null) {
      assert false;
      return null;
    }

    UtilityMethodForCodeOptimizations throwMethod =
        methodSynthesizerConsumer.synthesizeMethod(appView, eventConsumer, methodProcessingContext);
    ProgramMethod throwProgramMethod = throwMethod.uncheckedGetMethod();
    return internalGetThrowInstructionsForInvoke(
        invokedMethod, invokeType, localStackAllocator, throwProgramMethod);
  }

  private static Collection<CfInstruction> internalGetThrowInstructionsForInvoke(
      DexMethod invokedMethod,
      InvokeType invokeType,
      LocalStackAllocator localStackAllocator,
      ProgramMethod throwProgramMethod) {
    // Replace the entire effect of the invoke by a call to the throwing helper:
    //   ...
    //   invoke <method> [receiver] args*
    // =>
    //   ...
    //   (pop arg)*
    //   [pop receiver]
    //   invoke <throwing-method>
    //   pop exception result
    //   [push fake result for <method>]
    ArrayList<CfInstruction> replacement = new ArrayList<>();
    DexTypeList parameters = invokedMethod.getParameters();
    for (int i = parameters.values.length - 1; i >= 0; i--) {
      replacement.add(
          new CfStackInstruction(
              parameters.get(i).isWideType()
                  ? CfStackInstruction.Opcode.Pop2
                  : CfStackInstruction.Opcode.Pop));
    }
    if (!invokeType.isStatic()) {
      replacement.add(new CfStackInstruction(CfStackInstruction.Opcode.Pop));
    }

    CfInvoke throwInvoke =
        new CfInvoke(
            org.objectweb.asm.Opcodes.INVOKESTATIC, throwProgramMethod.getReference(), false);
    assert throwInvoke.getMethod().getReturnType().isClassType();
    replacement.add(throwInvoke);
    replacement.add(new CfStackInstruction(CfStackInstruction.Opcode.Pop));

    DexType returnType = invokedMethod.getReturnType();
    if (!returnType.isVoidType()) {
      replacement.add(
          returnType.isPrimitiveType()
              ? new CfConstNumber(0, ValueType.fromDexType(returnType))
              : new CfConstNull());
    } else {
      // If the return type is void, the stack may need an extra slot to fit the return type of
      // the call to the throwing method.
      localStackAllocator.allocateLocalStack(1);
    }
    return replacement;
  }

  private static MethodSynthesizerConsumer getMethodSynthesizerForThrowing(
      AppView<?> appView,
      InvokeType invokeType,
      MethodResolutionResult resolutionResult,
      ProgramMethod context) {
    if (resolutionResult == null) {
      return UtilityMethodsForCodeOptimizations::synthesizeThrowNoSuchMethodErrorMethod;
    } else if (resolutionResult.isSingleResolution()) {
      if (resolutionResult.getResolvedMethod().isStatic() != invokeType.isStatic()) {
        return UtilityMethodsForCodeOptimizations
            ::synthesizeThrowIncompatibleClassChangeErrorMethod;
      } else if (invokeType.isDirectOrSuper()
          && resolutionResult.getResolvedMethod().getAccessFlags().isAbstract()) {
        return UtilityMethodsForCodeOptimizations::synthesizeThrowAbstractMethodErrorMethod;
      }
    } else if (resolutionResult.isFailedResolution()) {
      FailedResolutionResult failedResolutionResult = resolutionResult.asFailedResolution();
      AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
      if (failedResolutionResult.isIllegalAccessErrorResult(
          context.getHolder(), appView, appInfo)) {
        return UtilityMethodsForCodeOptimizations::synthesizeThrowIllegalAccessErrorMethod;
      } else if (failedResolutionResult.isNoSuchMethodErrorResult(
          context.getHolder(), appView, appInfo)) {
        return UtilityMethodsForCodeOptimizations::synthesizeThrowNoSuchMethodErrorMethod;
      } else if (failedResolutionResult.isIncompatibleClassChangeErrorResult()) {
        return UtilityMethodsForCodeOptimizations
            ::synthesizeThrowIncompatibleClassChangeErrorMethod;
      }
    }

    return null;
  }
}
