// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRFinalizer;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.LirConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.apimodel.ApiInvokeOutlinerDesugaring;
import com.android.tools.r8.ir.desugar.apimodel.LirToLirApiInvokeOutlinerDesugaring;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.LirToLirDesugaredLibraryApiConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer.DesugaredLibraryDisableDesugarer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer.LirToLirDesugaredLibraryDisableDesugarer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryLibRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.LirToLirDesugaredLibraryLibRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.LirToLirDesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.android.tools.r8.ir.desugar.itf.LirToLirInterfaceMethodRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.ProgramMethodSet.ConcurrentProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class R8LibraryDesugaring {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final InternalOptions options;

  public R8LibraryDesugaring(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.options = appView.options();
  }

  public static void runIfNecessary(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    InternalOptions options = appView.options();
    if (options.isDesugaring()) {
      if (options.getLibraryDesugaringOptions().isEnabled()
          && options.getLibraryDesugaringOptions().isLirToLirLibraryDesugaringEnabled()
          && options.isGeneratingDex()) {
        new R8LibraryDesugaring(appView).run(executorService, timing);
      } else {
        assert !appView.options().apiModelingOptions().isLirToLirApiOutliningEnabled();
      }
    }
  }

  private void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    // Bring the LIR up-to-date.
    LirConverter.rewriteLirWithLens(appView, timing, executorService);

    // In R8 partial, commit the D8 classes to the app so that they will also be subject to library
    // desugaring.
    partialSubCompilationSetup();

    // Apply library desugaring.
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    ConcurrentProgramMethodSet synthesizedMethods = ProgramMethodSet.createConcurrent();
    runInstructionDesugaring(profileCollectionAdditions, synthesizedMethods, executorService);
    runPostProcessingDesugaring(
        profileCollectionAdditions, synthesizedMethods, executorService, timing);

    // Commit profile updates and convert synthesized methods to DEX.
    profileCollectionAdditions.commit(appView);
    processSynthesizedMethods(synthesizedMethods, executorService);

    // Commit pending synthetics.
    appView.rebuildAppInfo();

    // In R8 partial, uncommit the D8 classes from the app so that they will not be subject to whole
    // program optimizations.
    partialSubCompilationTearDown();

    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
  }

  private void runInstructionDesugaring(
      ProfileCollectionAdditions profileCollectionAdditions,
      ConcurrentProgramMethodSet synthesizedMethods,
      ExecutorService executorService)
      throws ExecutionException {
    CfInstructionDesugaringEventConsumer eventConsumer =
        CfInstructionDesugaringEventConsumer.createForR8LibraryDesugaring(
            appView, profileCollectionAdditions, synthesizedMethods);
    LirToLirApiInvokeOutlinerDesugaring apiOutliner =
        ApiInvokeOutlinerDesugaring.createLirToLir(
            appView, appView.apiLevelCompute(), eventConsumer);
    LirToLirDesugaredLibraryDisableDesugarer desugaredLibraryDisableDesugarer =
        DesugaredLibraryDisableDesugarer.createLirToLir(appView);
    LirToLirDesugaredLibraryLibRewriter desugaredLibraryLibRewriter =
        DesugaredLibraryLibRewriter.createLirToLir(appView, eventConsumer);
    LirToLirDesugaredLibraryRetargeter desugaredLibraryRetargeter =
        LirToLirDesugaredLibraryRetargeter.createLirToLir(appView, eventConsumer);
    LirToLirInterfaceMethodRewriter interfaceMethodRewriter =
        InterfaceMethodRewriter.createLirToLir(appView, eventConsumer);
    LirToLirDesugaredLibraryApiConverter desugaredLibraryApiConverter =
        DesugaredLibraryAPIConverter.createForLirToLir(
            appView, eventConsumer, interfaceMethodRewriter);
    ProcessorContext processorContext = appView.createProcessorContext();
    LensCodeRewriterUtils emptyRewriterUtils = LensCodeRewriterUtils.empty();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz ->
            clazz.forEachProgramMethodMatching(
                method -> method.hasCode() && method.getCode().isLirCode(),
                method -> {
                  MethodProcessingContext methodProcessingContext =
                      processorContext.createMethodProcessingContext(method);
                  R8LibraryDesugaringGraphLens libraryDesugaringGraphLens =
                      new R8LibraryDesugaringGraphLens(
                          appView,
                          apiOutliner,
                          desugaredLibraryApiConverter,
                          desugaredLibraryDisableDesugarer,
                          desugaredLibraryLibRewriter,
                          desugaredLibraryRetargeter,
                          interfaceMethodRewriter,
                          eventConsumer,
                          method,
                          methodProcessingContext);
                  LirConverter.rewriteLirMethodWithLens(
                      method, appView, libraryDesugaringGraphLens, emptyRewriterUtils);
                }),
        options.getThreadingModule(),
        executorService);
    appView.dexItemFactory().clearTypeElementsCache();

    // Move the pending methods and mark them live and ready for tracing.
    List<ProgramMethod> needsProcessing = eventConsumer.finalizeDesugaring();
    assert needsProcessing.isEmpty();

    if (desugaredLibraryApiConverter != null) {
      desugaredLibraryApiConverter.generateTrackingWarnings();
    }

    // Commit pending synthetics.
    appView.rebuildAppInfo();
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
  }

  private void runPostProcessingDesugaring(
      ProfileCollectionAdditions profileCollectionAdditions,
      ConcurrentProgramMethodSet synthesizedMethods,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    CfPostProcessingDesugaringEventConsumer eventConsumer =
        CfPostProcessingDesugaringEventConsumer.createForR8LirToLirLibraryDesugaring(
            appView, profileCollectionAdditions, synthesizedMethods);
    InterfaceMethodProcessorFacade interfaceDesugaring =
        InterfaceMethodProcessorFacade.createForR8LirToLirLibraryDesugaring(appView);
    CfPostProcessingDesugaringCollection.createForR8LirToLirLibraryDesugaring(
            appView, interfaceDesugaring)
        .postProcessingDesugaring(
            appView.appInfo().classes(), eventConsumer, executorService, timing);
  }

  private void processSynthesizedMethods(
      ProgramMethodSet synthesizedMethods, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        synthesizedMethods,
        method -> {
          IRCode code = method.buildIR(appView, MethodConversionOptions.forLirPhase(appView));
          DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
          IRFinalizer<?> finalizer =
              code.getConversionOptions().getFinalizer(deadCodeRemover, appView);
          Code lirCode =
              finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
          method.setCode(lirCode, appView);
        },
        options.getThreadingModule(),
        executorService);
  }

  private void partialSubCompilationSetup() {
    if (options.partialSubCompilationConfiguration != null) {
      options.partialSubCompilationConfiguration.asR8().commitDexingOutputClasses(appView);
    }
  }

  private void partialSubCompilationTearDown() {
    if (options.partialSubCompilationConfiguration != null) {
      options.partialSubCompilationConfiguration.asR8().uncommitDexingOutputClasses(appView);
    }
  }
}
