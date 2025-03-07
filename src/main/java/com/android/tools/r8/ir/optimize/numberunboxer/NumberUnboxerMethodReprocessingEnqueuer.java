// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Finds the methods in the program that should be reprocessed due to number unboxing. */
public class NumberUnboxerMethodReprocessingEnqueuer {

  private final AppView<AppInfoWithLiveness> appView;
  private final NumberUnboxerLens numberUnboxerLens;

  public NumberUnboxerMethodReprocessingEnqueuer(
      AppView<AppInfoWithLiveness> appView, NumberUnboxerLens numberUnboxerLens) {
    this.appView = appView;
    this.numberUnboxerLens = numberUnboxerLens;
  }

  public void enqueueMethodsForReprocessing(
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Enqueue methods for reprocessing due to the number unboxer");
    assert appView.graphLens() == numberUnboxerLens;
    postMethodProcessorBuilder.rewrittenWithLens(appView);
    enqueueAffectedCallees(postMethodProcessorBuilder);
    enqueueAffectedCallers(postMethodProcessorBuilder, executorService);
    timing.end();
  }

  boolean shouldReprocess(DexMethod reference) {
    return reference.isNotIdenticalTo(numberUnboxerLens.getPreviousMethodSignature(reference));
  }

  private void enqueueAffectedCallees(PostMethodProcessor.Builder postMethodProcessorBuilder) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            if (method.getDefinition().getCode().isSharedCodeObject()) {
              return;
            }
            if (shouldReprocess(method.getReference())) {
              assert appView.getKeepInfo(method).isReprocessingAllowed(appView.options(), method);
              postMethodProcessorBuilder.add(method, numberUnboxerLens);
            }
          });
    }
  }

  // TODO(b/190154391): This could invalidate the @NeverReprocessMethod testing annotations (non
  //  critical). If @NeverReprocessMethod is used, we would need to scan the application to mark
  //  methods as unoptimizable prior to removing parameters from the application.
  private void enqueueAffectedCallers(
      PostMethodProcessor.Builder postMethodProcessorBuilder, ExecutorService executorService)
      throws ExecutionException {
    Collection<List<ProgramMethod>> methodsToReprocess =
        ThreadUtils.processItemsWithResultsThatMatches(
            appView.appInfo().classes(),
            clazz -> {
              List<ProgramMethod> methodsToReprocessInClass = new ArrayList<>();
              clazz.forEachProgramMethodMatching(
                  DexEncodedMethod::hasCode,
                  method -> {
                    AffectedMembersGraphLensUseRegistry registry =
                        new AffectedMembersGraphLensUseRegistry(appView, method);
                    if (method.registerCodeReferencesWithResult(registry)) {
                      assert !method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite();
                      methodsToReprocessInClass.add(method);
                    }
                  });
              return methodsToReprocessInClass;
            },
            res -> !res.isEmpty(),
            appView.options().getThreadingModule(),
            executorService);
    methodsToReprocess.forEach(
        methodsToReprocessInClass ->
            postMethodProcessorBuilder.addAll(methodsToReprocessInClass, numberUnboxerLens));
  }

  public class AffectedMembersGraphLensUseRegistry
      extends DefaultUseRegistryWithResult<Boolean, ProgramMethod> {

    public AffectedMembersGraphLensUseRegistry(
        AppView<AppInfoWithLiveness> appViewWithLiveness, ProgramMethod context) {
      super(appViewWithLiveness, context, false);
    }

    private void markAffected() {
      setResult(Boolean.TRUE);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerInvokeMethod(method, InvokeType.DIRECT);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvokeMethod(method, InvokeType.INTERFACE);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerInvokeMethod(method, InvokeType.STATIC);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      registerInvokeMethod(method, InvokeType.SUPER);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      registerInvokeMethod(method, InvokeType.VIRTUAL);
    }

    private void registerInvokeMethod(DexMethod method, InvokeType invokeType) {
      // TODO(b/314117865): This is assuming that there are no non-rebound method references.
      DexMethod reference =
          numberUnboxerLens
              .lookupMethod(
                  method, getContext().getReference(), invokeType, numberUnboxerLens.getPrevious())
              .getReference();
      assert reference != null;
      if (shouldReprocess(reference)) {
        markAffected();
      }
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field);
    }

    private void registerFieldAccess(DexField unused) {
      // TODO(b/307872552): Deal with unboxed fields.
    }
  }
}
