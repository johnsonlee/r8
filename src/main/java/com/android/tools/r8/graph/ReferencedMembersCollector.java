// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ReferencedMembersCollector {

  public interface ReferencedMembersConsumer {

    void onFieldReference(DexField field, ProgramMethod context);

    void onMethodReference(DexMethod method, ProgramMethod context);
  }

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ReferencedMembersConsumer consumer;

  public ReferencedMembersCollector(
      AppView<? extends AppInfoWithClassHierarchy> appView, ReferencedMembersConsumer consumer) {
    this.appView = appView;
    this.consumer = consumer;
  }

  public ReferencedMembersCollector run(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        this::processClass,
        appView.options().getThreadingModule(),
        executorService);
    return this;
  }

  // In R8 partial, there can be non-rebound field and method references in the D8 partition, which
  // resolve to definitions in the R8 partition. This makes sure that we also record the minified
  // names for these non-rebound field and method references.
  public void runForR8Partial(ExecutorService executorService) throws ExecutionException {
    if (appView.options().partialSubCompilationConfiguration == null) {
      return;
    }
    ThreadUtils.processItems(
        appView.options().partialSubCompilationConfiguration.asR8().getDexingOutputClasses(),
        this::processClass,
        appView.options().getThreadingModule(),
        executorService);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachProgramMethodMatching(DexEncodedMethod::hasCode, this::processMethod);
  }

  private void processMethod(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (code.isCfCode()) {
      assert appView.isCfByteCodePassThrough(method);
      processCfCode(method, code.asCfCode());
    } else if (code.isDefaultInstanceInitializerCode()) {
      processDefaultInstanceInitializerCode(method);
    } else if (code.isLirCode()) {
      processLirCode(method, code.asLirCode());
    } else if (code.isThrowExceptionCode()) {
      processThrowExceptionCode(method, code.asThrowExceptionCode());
    } else if (code.isThrowNullCode()) {
      // Intentionally empty.
    } else {
      assert false : code.getClass().getName();
    }
  }

  private void processDefaultInstanceInitializerCode(ProgramMethod method) {
    DexMethod invokedMethod =
        DefaultInstanceInitializerCode.getParentConstructor(method, appView.dexItemFactory());
    consumer.onMethodReference(invokedMethod, method);
  }

  private void processCfCode(ProgramMethod method, CfCode code) {
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isFieldInstruction()) {
        consumer.onFieldReference(instruction.asFieldInstruction().getField(), method);
      } else if (instruction.isInvoke()) {
        consumer.onMethodReference(instruction.asInvoke().getMethod(), method);
      } else if (instruction.isInvokeDynamic()) {
        processCallSite(method, instruction.asInvokeDynamic().getCallSite());
      }
    }
  }

  private void processLirCode(ProgramMethod method, LirCode<Integer> code) {
    for (LirConstant constant : code.getConstantPool()) {
      if (constant instanceof DexField) {
        consumer.onFieldReference((DexField) constant, method);
      } else if (constant instanceof DexCallSite) {
        processCallSite(method, (DexCallSite) constant);
      } else if (constant instanceof DexMethod) {
        consumer.onMethodReference((DexMethod) constant, method);
      } else if (constant instanceof DexMethodHandle) {
        processMethodHandle(method, (DexMethodHandle) constant);
      }
    }
  }

  private void processThrowExceptionCode(ProgramMethod method, ThrowExceptionCode code) {
    DexMethod constructor =
        appView.dexItemFactory().createInstanceInitializer(code.getExceptionType());
    consumer.onMethodReference(constructor, method);
  }

  private void processCallSite(ProgramMethod method, DexCallSite callSite) {
    processMethodHandle(method, callSite.getBootstrapMethod());
    for (DexValue bootstrapArg : callSite.getBootstrapArgs()) {
      if (bootstrapArg.isDexValueField()) {
        consumer.onFieldReference(bootstrapArg.asDexValueField().getValue(), method);
      } else if (bootstrapArg.isDexValueMethod()) {
        consumer.onMethodReference(bootstrapArg.asDexValueMethod().getValue(), method);
      } else if (bootstrapArg.isDexValueMethodHandle()) {
        processMethodHandle(method, bootstrapArg.asDexValueMethodHandle().getValue());
      }
    }
  }

  private void processMethodHandle(ProgramMethod method, DexMethodHandle methodHandle) {
    if (methodHandle.isFieldHandle()) {
      consumer.onFieldReference(methodHandle.asField(), method);
    } else {
      assert methodHandle.isMethodHandle();
      consumer.onMethodReference(methodHandle.asMethod(), method);
    }
  }
}
