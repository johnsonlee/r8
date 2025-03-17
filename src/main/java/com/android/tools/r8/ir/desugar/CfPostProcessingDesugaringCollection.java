// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPICallbackSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer.DesugaredLibraryDisableDesugarerPostProcessor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.AutoCloseableRetargeterPostProcessor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterPostProcessor;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.records.RecordClassDesugaring;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public abstract class CfPostProcessingDesugaringCollection {

  public static CfPostProcessingDesugaringCollection create(
      AppView<?> appView,
      InterfaceMethodProcessorFacade interfaceMethodProcessorFacade,
      Predicate<ProgramMethod> isLiveMethod) {
    if (appView.options().desugarState.isOn()) {
      return NonEmptyCfPostProcessingDesugaringCollection.create(
          appView, interfaceMethodProcessorFacade, isLiveMethod);
    }
    return empty();
  }

  @SuppressWarnings("DoNotCallSuggester")
  public static CfPostProcessingDesugaringCollection createForR8LirToLirLibraryDesugaring(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      InterfaceMethodProcessorFacade interfaceDesugaring) {
    throw new Unreachable();
  }

  static CfPostProcessingDesugaringCollection empty() {
    return EmptyCfPostProcessingDesugaringCollection.getInstance();
  }

  public abstract void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException;

  public static class NonEmptyCfPostProcessingDesugaringCollection
      extends CfPostProcessingDesugaringCollection {

    private final List<CfPostProcessingDesugaring> desugarings;

    public NonEmptyCfPostProcessingDesugaringCollection(
        List<CfPostProcessingDesugaring> desugarings) {
      this.desugarings = desugarings;
    }

    public static CfPostProcessingDesugaringCollection create(
        AppView<?> appView,
        InterfaceMethodProcessorFacade interfaceMethodProcessorFacade,
        Predicate<ProgramMethod> isLiveMethod) {
      ArrayList<CfPostProcessingDesugaring> desugarings = new ArrayList<>();
      if (appView
              .options()
              .getLibraryDesugaringOptions()
              .getMachineDesugaredLibrarySpecification()
              .hasRetargeting()
          && !appView.options().getLibraryDesugaringOptions().isDesugaredLibraryCompilation()) {
        desugarings.add(new DesugaredLibraryRetargeterPostProcessor(appView));
      }
      if (appView.options().shouldDesugarAutoCloseable()) {
        desugarings.add(new AutoCloseableRetargeterPostProcessor(appView));
      }
      if (interfaceMethodProcessorFacade != null) {
        desugarings.add(interfaceMethodProcessorFacade);
      }
      DesugaredLibraryAPICallbackSynthesizer apiCallbackSynthesizor =
          appView.options().getLibraryDesugaringOptions().hasTypeRewriter()
              ? new DesugaredLibraryAPICallbackSynthesizer(appView, isLiveMethod)
              : null;
      // At this point the desugaredLibraryAPIConverter is required to be last to generate
      // call-backs on the forwarding methods.
      if (apiCallbackSynthesizor != null) {
        desugarings.add(apiCallbackSynthesizor);
      }
      RecordClassDesugaring recordRewriter = RecordClassDesugaring.create(appView);
      if (recordRewriter != null) {
        desugarings.add(recordRewriter);
      }
      DesugaredLibraryDisableDesugarerPostProcessor disableDesugarer =
          DesugaredLibraryDisableDesugarerPostProcessor.create(appView);
      if (disableDesugarer != null) {
        desugarings.add(disableDesugarer);
      }
      if (desugarings.isEmpty()) {
        return empty();
      }
      return new NonEmptyCfPostProcessingDesugaringCollection(desugarings);
    }

    @Override
    public void postProcessingDesugaring(
        Collection<DexProgramClass> programClasses,
        CfPostProcessingDesugaringEventConsumer eventConsumer,
        ExecutorService executorService,
        Timing timing)
        throws ExecutionException {
      Collection<DexProgramClass> sortedProgramClasses =
          CollectionUtils.sort(programClasses, Comparator.comparing(DexClass::getType));
      for (CfPostProcessingDesugaring desugaring : desugarings) {
        desugaring.postProcessingDesugaring(
            sortedProgramClasses, eventConsumer, executorService, timing);
      }
    }
  }

  public static class EmptyCfPostProcessingDesugaringCollection
      extends CfPostProcessingDesugaringCollection {

    private static final EmptyCfPostProcessingDesugaringCollection INSTANCE =
        new EmptyCfPostProcessingDesugaringCollection();

    private EmptyCfPostProcessingDesugaringCollection() {}

    private static EmptyCfPostProcessingDesugaringCollection getInstance() {
      return INSTANCE;
    }

    @Override
    public void postProcessingDesugaring(
        Collection<DexProgramClass> programClasses,
        CfPostProcessingDesugaringEventConsumer eventConsumer,
        ExecutorService executorService,
        Timing timing) {
      // Intentionally empty.
    }
  }
}
