// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.singlecaller;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.lightir.LirInstructionView;
import com.android.tools.r8.lightir.LirOpcodeUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ObjectUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class SingleCallerScanner {

  private static final ProgramMethod MULTIPLE_CALLERS = ProgramMethod.createSentinel();

  private final AppView<AppInfoWithLiveness> appView;
  private final ProgramMethodSet monomorphicVirtualMethods;

  SingleCallerScanner(
      AppView<AppInfoWithLiveness> appView, ProgramMethodSet monomorphicVirtualMethods) {
    this.appView = appView;
    this.monomorphicVirtualMethods = monomorphicVirtualMethods;
  }

  public ProgramMethodMap<ProgramMethod> getSingleCallerMethods(ExecutorService executorService)
      throws ExecutionException {
    InternalOptions options = appView.options();
    ProgramMethodMap<ProgramMethod> singleCallerMethodCandidates =
        traceConstantPools(executorService);
    singleCallerMethodCandidates.removeIf(
        (callee, caller) ->
            callee.getDefinition().isLibraryMethodOverride().isPossiblyTrue()
                || !appView.getKeepInfo(callee).isSingleCallerInliningAllowed(options, callee)
                || !AndroidApiLevelUtils.isApiSafeForInlining(caller, callee, appView));
    return traceInstructions(singleCallerMethodCandidates, executorService);
  }

  private ProgramMethodMap<ProgramMethod> traceConstantPools(ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodMap<ProgramMethod> traceResult = ProgramMethodMap.createConcurrent();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> recordCallEdges(clazz, traceResult),
        appView.options().getThreadingModule(),
        executorService);
    ProgramMethodMap<ProgramMethod> singleCallerMethodCandidates =
        ProgramMethodMap.createConcurrent();
    traceResult.forEach(
        (callee, caller) -> {
          if (callee.getDefinition().hasCode()
              && ObjectUtils.notIdentical(caller, MULTIPLE_CALLERS)
              && !callee.isStructurallyEqualTo(caller)) {
            singleCallerMethodCandidates.put(callee, caller);
          }
        });
    return singleCallerMethodCandidates;
  }

  private void recordCallEdges(
      DexProgramClass clazz, ProgramMethodMap<ProgramMethod> singleCallerMethods) {
    clazz.forEachProgramMethodMatching(
        method -> method.hasCode() && method.getCode().isLirCode(),
        method -> recordCallEdges(method, singleCallerMethods));
  }

  private void recordCallEdges(
      ProgramMethod method, ProgramMethodMap<ProgramMethod> singleCallerMethods) {
    ProgramMethodMap<ProgramMethod> threadLocalSingleCallerMethods = ProgramMethodMap.create();
    LirCode<Integer> code = method.getDefinition().getCode().asLirCode();
    for (LirConstant constant : code.getConstantPool()) {
      if (constant instanceof DexCallSite) {
        traceCallSiteConstant(method, (DexCallSite) constant, threadLocalSingleCallerMethods);
      } else if (constant instanceof DexMethodHandle) {
        traceMethodHandleConstant(
            method, (DexMethodHandle) constant, threadLocalSingleCallerMethods);
      } else if (constant instanceof DexMethod) {
        traceMethodConstant(method, (DexMethod) constant, threadLocalSingleCallerMethods);
      }
    }
    threadLocalSingleCallerMethods.forEach(
        (callee, caller) -> {
          if (ObjectUtils.identical(caller, MULTIPLE_CALLERS)) {
            singleCallerMethods.put(callee, MULTIPLE_CALLERS);
          } else {
            recordCallEdge(caller, callee, singleCallerMethods);
          }
        });
  }

  private void traceCallSiteConstant(
      ProgramMethod method,
      DexCallSite callSite,
      ProgramMethodMap<ProgramMethod> threadLocalSingleCallerMethods) {
    LambdaDescriptor descriptor =
        LambdaDescriptor.tryInfer(callSite, appView, appView.appInfo(), method);
    if (descriptor != null) {
      traceMethodHandleConstant(method, descriptor.implHandle, threadLocalSingleCallerMethods);
    } else {
      traceMethodHandleConstant(method, callSite.bootstrapMethod, threadLocalSingleCallerMethods);
      for (DexValue bootstrapArg : callSite.getBootstrapArgs()) {
        if (bootstrapArg.isDexValueMethodHandle()) {
          traceMethodHandleConstant(
              method,
              bootstrapArg.asDexValueMethodHandle().getValue(),
              threadLocalSingleCallerMethods);
        }
      }
    }
  }

  private void traceMethodHandleConstant(
      ProgramMethod method,
      DexMethodHandle methodHandle,
      ProgramMethodMap<ProgramMethod> threadLocalSingleCallerMethods) {
    if (!methodHandle.isMethodHandle()) {
      return;
    }
    traceMethodConstant(method, methodHandle.asMethod(), threadLocalSingleCallerMethods);
  }

  private void traceMethodConstant(
      ProgramMethod method,
      DexMethod referencedMethod,
      ProgramMethodMap<ProgramMethod> threadLocalSingleCallerMethods) {
    if (referencedMethod.getHolderType().isArrayType()) {
      return;
    }
    ProgramMethod resolvedMethod =
        appView
            .appInfo()
            .unsafeResolveMethodDueToDexFormat(referencedMethod)
            .getResolvedProgramMethod();
    if (resolvedMethod != null) {
      recordCallEdge(method, resolvedMethod, threadLocalSingleCallerMethods);
    }
  }

  private void recordCallEdge(
      ProgramMethod caller, ProgramMethod callee, ProgramMethodMap<ProgramMethod> callers) {
    callers.compute(
        callee, (ignore, existingCallers) -> existingCallers != null ? MULTIPLE_CALLERS : caller);
  }

  private ProgramMethodMap<ProgramMethod> traceInstructions(
      ProgramMethodMap<ProgramMethod> singleCallerMethodCandidates, ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodMap<ProgramMethodSet> callersToCallees = ProgramMethodMap.create();
    singleCallerMethodCandidates.forEach(
        (callee, caller) ->
            callersToCallees
                .computeIfAbsent(caller, ignoreKey(ProgramMethodSet::create))
                .add(callee));
    ThreadUtils.processItems(
        callersToCallees.streamKeys()::forEach,
        caller -> {
          ProgramMethodSet callees = callersToCallees.get(caller);
          LirCode<Integer> code = caller.getDefinition().getCode().asLirCode();
          ProgramMethodMap<Integer> counters = ProgramMethodMap.create();
          for (LirInstructionView view : code) {
            int opcode = view.getOpcode();
            if (!LirOpcodeUtils.isInvokeMethod(opcode)) {
              continue;
            }
            DexMethod invokedMethod =
                (DexMethod) code.getConstantItem(view.getNextConstantOperand());
            ProgramMethod resolvedMethod =
                appView
                    .appInfo()
                    .resolveMethod(
                        invokedMethod, LirOpcodeUtils.getInterfaceBitFromInvokeOpcode(opcode))
                    .getResolvedProgramMethod();
            if (resolvedMethod == null || !callees.contains(resolvedMethod)) {
              continue;
            }
            if (resolvedMethod.getAccessFlags().belongsToVirtualPool()
                && !monomorphicVirtualMethods.contains(resolvedMethod)) {
              continue;
            }
            counters.put(resolvedMethod, counters.getOrDefault(resolvedMethod, 0) + 1);
          }
          callees.forEach(
              (callee) -> {
                if (!counters.containsKey(callee) || counters.get(callee) > 1) {
                  singleCallerMethodCandidates.remove(callee);
                }
              });
        },
        appView.options().getThreadingModule(),
        executorService);
    return singleCallerMethodCandidates;
  }
}
