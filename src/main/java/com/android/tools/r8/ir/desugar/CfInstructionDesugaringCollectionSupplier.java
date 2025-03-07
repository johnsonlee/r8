// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor;
import com.android.tools.r8.utils.timing.Timing;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public interface CfInstructionDesugaringCollectionSupplier {

  CfInstructionDesugaringCollectionSupplier EMPTY =
      new EmptyCfInstructionDesugaringCollectionSupplier();

  CfInstructionDesugaringCollection get(ProgramDefinition definition);

  void finalizeNestDesugaring();

  void generateDesugaredLibraryApiConverterTrackingWarnings(Timing timing);

  InterfaceMethodProcessorFacade getInterfaceMethodProcessorFacade(
      InterfaceProcessor interfaceProcessor);

  void processClasspath(D8MethodProcessor methodProcessor, ExecutorService executor, Timing timing)
      throws ExecutionException;

  static CfInstructionDesugaringCollectionSupplier createForD8(AppView<?> appView) {
    return new DefaultCfInstructionDesugaringCollectionSupplier(appView);
  }

  static CfInstructionDesugaringCollectionSupplier createForL8(AppView<?> appView) {
    return new DefaultCfInstructionDesugaringCollectionSupplier(appView);
  }

  static CfInstructionDesugaringCollectionSupplier empty() {
    return EMPTY;
  }

  class DefaultCfInstructionDesugaringCollectionSupplier
      implements CfInstructionDesugaringCollectionSupplier {

    private final CfInstructionDesugaringCollection desugaring;

    DefaultCfInstructionDesugaringCollectionSupplier(AppView<?> appView) {
      this.desugaring = CfInstructionDesugaringCollection.create(appView);
    }

    @Override
    public CfInstructionDesugaringCollection get(ProgramDefinition target) {
      return desugaring;
    }

    @Override
    public void finalizeNestDesugaring() {
      desugaring.withD8NestBasedAccessDesugaring(
          nestBasedAccessDesugaring -> {
            nestBasedAccessDesugaring.reportDesugarDependencies();
            nestBasedAccessDesugaring.clearNestAttributes();
          });
    }

    @Override
    public void generateDesugaredLibraryApiConverterTrackingWarnings(Timing timing) {
      try (Timing t0 = timing.begin("Generate desugared library api converter tracking warnings")) {
        desugaring.withDesugaredLibraryAPIConverter(
            DesugaredLibraryAPIConverter::generateTrackingWarnings);
      }
    }

    @Override
    public InterfaceMethodProcessorFacade getInterfaceMethodProcessorFacade(
        InterfaceProcessor interfaceProcessor) {
      return desugaring.withInterfaceMethodRewriter(
          interfaceMethodRewriter ->
              interfaceMethodRewriter.getPostProcessingDesugaringD8(interfaceProcessor));
    }

    @Override
    public void processClasspath(
        D8MethodProcessor methodProcessor, ExecutorService executor, Timing timing)
        throws ExecutionException {
      try (Timing t0 = timing.begin("Process classpath for desugaring")) {
        desugaring.withD8NestBasedAccessDesugaring(
            nestBasedAccessDesugaring ->
                nestBasedAccessDesugaring.synthesizeBridgesForNestBasedAccessesOnClasspath(
                    methodProcessor, executor));
      }
    }
  }

  class EmptyCfInstructionDesugaringCollectionSupplier
      implements CfInstructionDesugaringCollectionSupplier {

    @Override
    public CfInstructionDesugaringCollection get(ProgramDefinition target) {
      return CfInstructionDesugaringCollection.empty();
    }

    @Override
    public void finalizeNestDesugaring() {
      // Intentionally empty.
    }

    @Override
    public void generateDesugaredLibraryApiConverterTrackingWarnings(Timing timing) {
      // Intentionally empty.
    }

    @Override
    public InterfaceMethodProcessorFacade getInterfaceMethodProcessorFacade(
        InterfaceProcessor interfaceProcessor) {
      return null;
    }

    @Override
    public void processClasspath(
        D8MethodProcessor methodProcessor, ExecutorService executor, Timing timing) {
      // Intentionally empty.
    }
  }
}
