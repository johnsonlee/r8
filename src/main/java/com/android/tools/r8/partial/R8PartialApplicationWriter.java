// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.dex.code.DexIgetOrIputOrSgetOrSput;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeMethodOrInvokeMethodRange;
import com.android.tools.r8.dex.code.DexNewInstance;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ThrowExceptionCode;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.LirConverter;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialD8SubCompilationConfiguration;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class R8PartialApplicationWriter {

  private final AppView<AppInfo> appView;
  private final InternalOptions options;
  private final R8PartialD8SubCompilationConfiguration subCompilationConfiguration;

  public R8PartialApplicationWriter(AppView<AppInfo> appView) {
    this.appView = appView;
    this.options = appView.options();
    this.subCompilationConfiguration = appView.options().partialSubCompilationConfiguration.asD8();
  }

  public void write(ExecutorService executorService) throws ExecutionException {
    assert appView.getNamingLens().isIdentityLens();
    // We need to rewrite the code with the lenses here, since the D8 lenses are normally applied
    // during writing, which we bypass. The D8 lenses may arise from synthetic finalization and
    // horizontal class merging of synthetics.
    rewriteCodeWithLens(executorService);
    subCompilationConfiguration.writeApplication(appView);
  }

  private void rewriteCodeWithLens(ExecutorService executorService) throws ExecutionException {
    if (appView.graphLens() == appView.codeLens()) {
      assert appView.graphLens().isIdentityLens();
      return;
    }
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        this::rewriteCodeWithLens,
        options.getThreadingModule(),
        executorService);
  }

  private void rewriteCodeWithLens(DexProgramClass clazz) {
    clazz.forEachProgramMethod(this::rewriteCodeWithLens);
  }

  private void rewriteCodeWithLens(ProgramMethod method) {
    if (!method.getDefinition().hasCode()) {
      return;
    }
    Code code = method.getDefinition().getCode();
    if (code.isCfCode()) {
      rewriteCfCodeWithLens(code.asCfCode(), method);
    } else if (code.isDexCode()) {
      rewriteDexCodeWithLens(code.asDexCode(), method);
    } else if (code.isLirCode()) {
      LirConverter.rewriteLirMethodWithLens(
          method, appView, appView.graphLens(), LensCodeRewriterUtils.empty(), Timing.empty());
    } else if (code.isThrowExceptionCode()) {
      assert verifyThrowExceptionCodeIsUpToDate(method, code.asThrowExceptionCode());
    } else {
      assert false;
    }
  }

  private void rewriteCfCodeWithLens(CfCode code, ProgramMethod method) {
    ListUtils.map(
        code.getInstructions(),
        i -> rewriteCfInstructionWithLens(i, method),
        code::setInstructions);
  }

  private CfInstruction rewriteCfInstructionWithLens(
      CfInstruction instruction, ProgramMethod context) {
    if (instruction.isInvoke()) {
      CfInvoke invoke = instruction.asInvoke();
      DexMethod invokedMethod = invoke.getMethod();
      MethodLookupResult lookupResult =
          appView
              .graphLens()
              .lookupMethod(
                  invokedMethod,
                  context.getReference(),
                  invoke.getInvokeType(appView, appView.codeLens(), context),
                  appView.codeLens());
      DexMethod rewrittenInvokedMethod = lookupResult.getReference();
      if (rewrittenInvokedMethod.isNotIdenticalTo(invokedMethod)) {
        return invoke.withMethod(rewrittenInvokedMethod, invoke.isInterface());
      }
    }
    if (instruction.isNew()) {
      CfNew cfNew = instruction.asNew();
      DexType type = cfNew.getType();
      DexType rewrittenType = appView.graphLens().lookupType(type, appView.codeLens());
      if (rewrittenType.isNotIdenticalTo(type)) {
        return cfNew.withType(rewrittenType);
      }
    } else if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      DexField field = fieldInstruction.getField();
      DexField rewrittenField = appView.graphLens().lookupField(field, appView.codeLens());
      if (rewrittenField.isNotIdenticalTo(field)) {
        return fieldInstruction.createWithField(rewrittenField);
      }
    }
    return instruction;
  }

  private void rewriteDexCodeWithLens(DexCode code, ProgramMethod method) {
    ArrayUtils.map(
        code.getInstructions(),
        i -> rewriteDexInstructionWithLens(i, method),
        DexInstruction.EMPTY_ARRAY,
        newInstructions -> method.setCode(code.withNewInstructions(newInstructions), appView));
  }

  private DexInstruction rewriteDexInstructionWithLens(
      DexInstruction instruction, ProgramMethod context) {
    if (instruction.isInvokeMethodOrInvokeMethodRange()) {
      DexInvokeMethodOrInvokeMethodRange invoke = instruction.asInvokeMethodOrInvokeMethodRange();
      DexMethod invokedMethod = invoke.getMethod();
      MethodLookupResult lookupResult =
          appView
              .graphLens()
              .lookupMethod(
                  invokedMethod, context.getReference(), invoke.getType(), appView.codeLens());
      DexMethod rewrittenInvokedMethod = lookupResult.getReference();
      if (rewrittenInvokedMethod.isNotIdenticalTo(invokedMethod)) {
        return invoke.withMethod(rewrittenInvokedMethod).asDexInstruction();
      }
    } else if (instruction.isNewInstance()) {
      DexNewInstance newInstance = instruction.asNewInstance();
      DexType type = newInstance.getType();
      DexType rewrittenType = appView.graphLens().lookupType(type, appView.codeLens());
      if (rewrittenType.isNotIdenticalTo(type)) {
        return newInstance.withType(rewrittenType);
      }
    } else if (instruction.isIgetOrIputOrSgetOrSput()) {
      DexIgetOrIputOrSgetOrSput fieldInstruction = instruction.asIgetOrIputOrSgetOrSput();
      DexField field = fieldInstruction.getField();
      DexField rewrittenField = appView.graphLens().lookupField(field, appView.codeLens());
      if (rewrittenField.isNotIdenticalTo(field)) {
        return fieldInstruction.withField(rewrittenField).asDexInstruction();
      }
    }
    return instruction;
  }

  private boolean verifyThrowExceptionCodeIsUpToDate(
      ProgramMethod method, ThrowExceptionCode code) {
    GraphLens graphLens = appView.graphLens();
    GraphLens codeLens = appView.codeLens();
    DexType exceptionType = code.asThrowExceptionCode().getExceptionType();
    DexMethod exceptionConstructor =
        appView.dexItemFactory().createInstanceInitializer(exceptionType);
    assert exceptionType.isIdenticalTo(graphLens.lookupType(exceptionType, codeLens));
    assert exceptionConstructor.isIdenticalTo(
        graphLens
            .lookupMethod(
                exceptionConstructor, method.getReference(), InvokeType.DIRECT, codeLens, false)
            .getReference());
    return true;
  }
}
