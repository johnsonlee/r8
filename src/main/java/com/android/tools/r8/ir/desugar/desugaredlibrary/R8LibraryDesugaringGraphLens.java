// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryConversionCfProvider;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.LirToLirDesugaredLibraryApiConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer.LirToLirDesugaredLibraryDisableDesugarer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.LirToLirDesugaredLibraryLibRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.LirToLirDesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.android.tools.r8.ir.optimize.CustomLensCodeRewriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

// TODO(b/391572031): Apply type instruction rewriting from CfToCfDesugaredLibraryDisableDesugarer.
public class R8LibraryDesugaringGraphLens extends DefaultNonIdentityGraphLens {

  private final LirToLirDesugaredLibraryApiConverter desugaredLibraryApiConverter;
  private final LirToLirDesugaredLibraryDisableDesugarer desugaredLibraryDisableDesugarer;
  private final LirToLirDesugaredLibraryLibRewriter desugaredLibraryLibRewriter;
  private final LirToLirDesugaredLibraryRetargeter desugaredLibraryRetargeter;

  @SuppressWarnings("UnusedVariable")
  private final InterfaceMethodRewriter interfaceMethodRewriter;

  private final CfInstructionDesugaringEventConsumer eventConsumer;
  private final ProgramMethod method;
  private final MethodProcessingContext methodProcessingContext;

  public R8LibraryDesugaringGraphLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      LirToLirDesugaredLibraryApiConverter desugaredLibraryApiConverter,
      LirToLirDesugaredLibraryDisableDesugarer desugaredLibraryDisableDesugarer,
      LirToLirDesugaredLibraryLibRewriter desugaredLibraryLibRewriter,
      LirToLirDesugaredLibraryRetargeter desugaredLibraryRetargeter,
      InterfaceMethodRewriter interfaceMethodRewriter,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext) {
    super(appView);
    this.desugaredLibraryApiConverter = desugaredLibraryApiConverter;
    this.desugaredLibraryDisableDesugarer = desugaredLibraryDisableDesugarer;
    this.desugaredLibraryLibRewriter = desugaredLibraryLibRewriter;
    this.desugaredLibraryRetargeter = desugaredLibraryRetargeter;
    this.interfaceMethodRewriter = interfaceMethodRewriter;
    this.eventConsumer = eventConsumer;
    this.method = method;
    this.methodProcessingContext = methodProcessingContext;
  }

  @Override
  public boolean hasCustomLensCodeRewriter() {
    return true;
  }

  @Override
  public CustomLensCodeRewriter getCustomLensCodeRewriter() {
    return new R8LibraryDesugaringLensCodeRewriter();
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    if (desugaredLibraryRetargeter != null) {
      FieldLookupResult result = desugaredLibraryRetargeter.lookupField(previous, method, this);
      if (result != previous) {
        return result;
      }
    }

    if (desugaredLibraryDisableDesugarer != null) {
      FieldLookupResult result =
          desugaredLibraryDisableDesugarer.lookupField(previous, method, this);
      if (result != previous) {
        return result;
      }
    }

    return previous;
  }

  @Override
  protected MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    // TODO(b/391572031): Implement invoke desugaring.
    assert previous.getPrototypeChanges().isEmpty();

    if (desugaredLibraryLibRewriter != null) {
      MethodLookupResult result =
          desugaredLibraryLibRewriter.lookupMethod(previous, method, methodProcessingContext, this);
      if (result != previous) {
        return result;
      }
    }

    if (desugaredLibraryRetargeter != null) {
      MethodLookupResult result =
          desugaredLibraryRetargeter.lookupMethod(previous, method, methodProcessingContext, this);
      if (result != previous) {
        return result;
      }
    }

    if (desugaredLibraryDisableDesugarer != null) {
      MethodLookupResult result =
          desugaredLibraryDisableDesugarer.lookupMethod(previous, method, this);
      if (result != previous) {
        return result;
      }
    }

    if (desugaredLibraryApiConverter != null) {
      return desugaredLibraryApiConverter.lookupMethod(
          previous, method, methodProcessingContext, this);
    }

    return previous;
  }

  private class R8LibraryDesugaringLensCodeRewriter implements CustomLensCodeRewriter {

    @Override
    public Set<Phi> rewriteCode(
        IRCode code,
        MethodProcessor methodProcessor,
        RewrittenPrototypeDescription prototypeChanges,
        NonIdentityGraphLens lens) {
      boolean changed = false;
      BasicBlockIterator blocks = code.listIterator();
      GraphLens codeLens = code.context().getDefinition().getCode().getCodeLens(appView);
      while (blocks.hasNext()) {
        BasicBlock block = blocks.next();
        BasicBlockInstructionListIterator instructions = block.listIterator();
        while (instructions.hasNext()) {
          InvokeMethod invoke = instructions.next().asInvokeMethod();
          if (invoke == null) {
            continue;
          }
          MethodLookupResult lookupResult =
              lookupMethod(
                  invoke.getInvokedMethod(),
                  code.context().getReference(),
                  invoke.getType(),
                  codeLens,
                  invoke.getInterfaceBit());
          if (lookupResult.isNeedsDesugaredLibraryApiConversionSet()) {
            rewriteInvoke(code, blocks, instructions, invoke);
          }
          changed = true;
        }
      }
      assert changed;
      return Collections.emptySet();
    }

    private void rewriteInvoke(
        IRCode code,
        BasicBlockIterator blocks,
        BasicBlockInstructionListIterator instructions,
        InvokeMethod invoke) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      DesugaredLibraryConversionCfProvider conversionCfProvider =
          desugaredLibraryApiConverter.getConversionCfProvider();
      DexMethod returnConversion =
          conversionCfProvider.computeReturnConversion(
              invokedMethod, false, eventConsumer, method, methodProcessingContext);
      DexMethod[] parameterConversions =
          conversionCfProvider.computeParameterConversions(
              invokedMethod, true, eventConsumer, method, methodProcessingContext);

      List<Instruction> replacementInstructions = new ArrayList<>();
      List<Value> convertedMethodArguments = new ArrayList<>(invoke.arguments().size());
      if (invoke.isInvokeMethodWithReceiver()) {
        convertedMethodArguments.add(invoke.asInvokeMethodWithReceiver().getReceiver());
      }
      if (!invokedMethod.getParameters().isEmpty()) {
        DexMethod parameterConversionTarget =
            conversionCfProvider.getParameterConversionTarget(
                parameterConversions, invokedMethod, true, eventConsumer, methodProcessingContext);
        if (parameterConversionTarget != null) {
          InvokeStatic parameterConversionInvoke =
              InvokeStatic.builder()
                  .setArguments(
                      invoke.isInvokeMethodWithReceiver()
                          ? invoke.arguments().subList(1, invoke.arguments().size())
                          : invoke.arguments())
                  .setFreshOutValue(code, dexItemFactory().objectArrayType.toTypeElement(appView))
                  .setMethod(parameterConversionTarget)
                  .setPosition(invoke)
                  .build();
          replacementInstructions.add(parameterConversionInvoke);

          for (int i = 0; i < parameterConversions.length; i++) {
            DexType parameterType =
                parameterConversions[i] != null
                    ? parameterConversions[i].getReturnType()
                    : invokedMethod.getParameter(i);
            ConstNumber arrayIndex =
                ConstNumber.builder()
                    .setFreshOutValue(code, TypeElement.getInt())
                    .setPositionForNonThrowingInstruction(invoke.getPosition(), appView.options())
                    .setValue(i)
                    .build();
            replacementInstructions.add(arrayIndex);
            ArrayGet arrayGet =
                ArrayGet.builder()
                    .setArrayValue(parameterConversionInvoke.outValue())
                    .setIndexValue(arrayIndex.outValue())
                    .setFreshOutValue(code, dexItemFactory().objectType.toTypeElement(appView))
                    .setPosition(invoke)
                    .build();
            replacementInstructions.add(arrayGet);
            if (parameterType.isPrimitiveType()) {
              CheckCast checkCast =
                  CheckCast.builder()
                      .setCastType(dexItemFactory().getBoxedForPrimitiveType(parameterType))
                      .setObject(arrayGet.outValue())
                      .setFreshOutValue(code, parameterType.toTypeElement(appView))
                      .setPosition(invoke)
                      .build();
              replacementInstructions.add(checkCast);
              InvokeVirtual unboxInvoke =
                  InvokeVirtual.builder()
                      .setFreshOutValue(code, parameterType.toTypeElement(appView))
                      .setMethod(dexItemFactory().getUnboxPrimitiveMethod(parameterType))
                      .setSingleArgument(checkCast.outValue())
                      .setPosition(invoke)
                      .build();
              replacementInstructions.add(unboxInvoke);
              convertedMethodArguments.add(unboxInvoke.outValue());
            } else {
              CheckCast checkCast =
                  CheckCast.builder()
                      .setCastType(parameterType)
                      .setObject(arrayGet.outValue())
                      .setFreshOutValue(code, parameterType.toTypeElement(appView))
                      .setPosition(invoke)
                      .build();
              replacementInstructions.add(checkCast);
              convertedMethodArguments.add(checkCast.outValue());
            }
          }
        } else {
          for (int i = 0; i < parameterConversions.length - 2; i++) {
            convertedMethodArguments.add(invoke.getArgumentForParameter(i));
          }
          for (int i = Math.max(0, parameterConversions.length - 2);
              i < parameterConversions.length;
              i++) {
            DexMethod convertArgumentMethod = parameterConversions[i];
            if (convertArgumentMethod != null) {
              InvokeStatic convertArgumentInvoke =
                  InvokeStatic.builder()
                      .setIsInterface(false)
                      .setMethod(convertArgumentMethod)
                      .setSingleArgument(invoke.getArgumentForParameter(i))
                      .setFreshOutValue(
                          code, convertArgumentMethod.getReturnType().toTypeElement(appView))
                      .setPosition(invoke)
                      .build();
              replacementInstructions.add(convertArgumentInvoke);
              convertedMethodArguments.add(convertArgumentInvoke.outValue());
            } else {
              convertedMethodArguments.add(invoke.getArgumentForParameter(i));
            }
          }
        }
      }

      DexMethod convertedMethod =
          conversionCfProvider.convertedMethod(
              invokedMethod, true, returnConversion, parameterConversions);
      InvokeMethod convertedMethodInvoke =
          invoke
              .newBuilder()
              .setArguments(convertedMethodArguments)
              .setIsInterface(invoke.getInterfaceBit())
              .setMethod(convertedMethod)
              .setPosition(invoke)
              .applyIf(
                  !convertedMethod.getReturnType().isVoidType(),
                  b ->
                      b.setFreshOutValue(
                          code, convertedMethod.getReturnType().toTypeElement(appView)))
              .build();
      replacementInstructions.add(convertedMethodInvoke);

      Value replacementValue;
      if (returnConversion != null) {
        assert returnConversion.getArity() == 1 || returnConversion.getArity() == 2;
        if (returnConversion.getArity() == 2) {
          // If there is a second parameter, pass the receiver.
          if (!invoke.isInvokeSuper()) {
            appView
                .reporter()
                .error(
                    "Cannot generate inlined api conversion for return type for "
                        + invokedMethod
                        + " in "
                        + method.toSourceString());
          }
        }
        InvokeStatic returnConversionCall =
            InvokeStatic.builder()
                .setMethod(returnConversion)
                .setSingleArgument(convertedMethodInvoke.outValue())
                .setFreshOutValue(code, returnConversion.getReturnType().toTypeElement(appView))
                .setIsInterface(false)
                .setPosition(invoke)
                .build();
        replacementInstructions.add(returnConversionCall);
        replacementValue = returnConversionCall.outValue();
      } else {
        replacementValue = convertedMethodInvoke.outValue();
      }

      if (invoke.hasOutValue()) {
        invoke.outValue().replaceUsers(replacementValue);
      }
      instructions.removeOrReplaceByDebugLocalRead();
      instructions.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
          code, blocks, replacementInstructions, appView.options());
    }
  }
}
