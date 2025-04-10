// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public class InterfaceMethodProcessorFacade implements CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final InterfaceProcessor interfaceProcessor;
  private final ClassProcessor classProcessor;

  public InterfaceMethodProcessorFacade(AppView<?> appView, InterfaceProcessor interfaceProcessor) {
    this(appView, interfaceProcessor, alwaysTrue());
  }

  InterfaceMethodProcessorFacade(
      AppView<?> appView,
      InterfaceProcessor interfaceProcessor,
      Predicate<ProgramMethod> isLiveMethod) {
    this.appView = appView;
    assert interfaceProcessor != null;
    this.interfaceProcessor = interfaceProcessor;
    this.classProcessor =
        new ClassProcessor(appView, isLiveMethod, interfaceProcessor.getDesugaringMode());
  }

  public static InterfaceMethodProcessorFacade createForR8LirToLirLibraryDesugaring(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    InterfaceProcessor processor = InterfaceProcessor.createLirToLir(appView);
    return processor != null ? new InterfaceMethodProcessorFacade(appView, processor) : null;
  }

  private boolean shouldProcess(DexProgramClass clazz) {
    return !appView.isAlreadyLibraryDesugared(clazz) && !clazz.originatesFromDexResource();
  }

  private void processClassesConcurrently(
      Collection<DexProgramClass> programClasses,
      InterfaceProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        ListUtils.filter(programClasses, this::shouldProcess),
        clazz -> classProcessor.process(clazz, eventConsumer),
        appView.options().getThreadingModule(),
        executorService);
    classProcessor.finalizeProcessing(eventConsumer, executorService);
    if (interfaceProcessor != null) {
      interfaceProcessor.finalizeProcessing();
    }
  }

  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin("Interface method processor facade")) {
      processClassesConcurrently(programClasses, eventConsumer, executorService);
    }
  }
}
