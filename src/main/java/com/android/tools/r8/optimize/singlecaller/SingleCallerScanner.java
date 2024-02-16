// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.singlecaller;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
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
import com.android.tools.r8.lightir.LirOpcodes;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class SingleCallerScanner {

  private static final ProgramMethod MULTIPLE_CALLERS = ProgramMethod.createSentinel();

  private final AppView<AppInfoWithLiveness> appView;

  SingleCallerScanner(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public ProgramMethodMap<ProgramMethod> getSingleCallerMethods(ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodMap<ProgramMethod> singleCallerMethodCandidates =
        traceConstantPools(executorService);
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
              && caller != MULTIPLE_CALLERS
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
          if (caller == MULTIPLE_CALLERS) {
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
    if (referencedMethod.isInstanceInitializer(appView.dexItemFactory())) {
      ProgramMethod referencedProgramMethod =
          appView
              .appInfo()
              .unsafeResolveMethodDueToDexFormat(referencedMethod)
              .getResolvedProgramMethod();
      if (referencedProgramMethod != null) {
        recordCallEdge(method, referencedProgramMethod, threadLocalSingleCallerMethods);
      }
    } else {
      DexProgramClass referencedProgramMethodHolder =
          asProgramClassOrNull(
              appView
                  .appInfo()
                  .definitionForWithoutExistenceAssert(referencedMethod.getHolderType()));
      ProgramMethod referencedProgramMethod =
          referencedMethod.lookupOnProgramClass(referencedProgramMethodHolder);
      if (referencedProgramMethod != null
          && referencedProgramMethod.getAccessFlags().isPrivate()
          && !referencedProgramMethod.getAccessFlags().isStatic()) {
        recordCallEdge(method, referencedProgramMethod, threadLocalSingleCallerMethods);
      }
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
            if (opcode != LirOpcodes.INVOKEDIRECT && opcode != LirOpcodes.INVOKEDIRECT_ITF) {
              continue;
            }
            DexMethod invokedMethod =
                (DexMethod) code.getConstantItem(view.getNextConstantOperand());
            ProgramMethod resolvedMethod =
                appView
                    .appInfo()
                    .resolveMethod(invokedMethod, opcode == LirOpcodes.INVOKEDIRECT_ITF)
                    .getResolvedProgramMethod();
            if (resolvedMethod != null && callees.contains(resolvedMethod)) {
              counters.put(resolvedMethod, counters.getOrDefault(resolvedMethod, 0) + 1);
            }
          }
          counters.forEach(
              (callee, counter) -> {
                if (counter > 1) {
                  singleCallerMethodCandidates.remove(callee);
                }
              });
        },
        appView.options().getThreadingModule(),
        executorService);
    return singleCallerMethodCandidates;
  }
}
