// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Converts synthetic class initializers that have been created as a result of merging class
 * initializers into a single class initializer to DEX.
 */
public class SyntheticInitializerConverter {

  private final AppView<?> appView;

  private final List<ProgramMethod> classInitializers;

  private SyntheticInitializerConverter(AppView<?> appView, List<ProgramMethod> classInitializers) {
    this.appView = appView;
    this.classInitializers = classInitializers;
  }

  public static Builder builder(AppView<?> appView) {
    return new Builder(appView);
  }

  public void convertClassInitializers(ExecutorService executorService) throws ExecutionException {
    if (!classInitializers.isEmpty()) {
      assert appView.dexItemFactory().verifyNoCachedTypeElements();
      IRConverter converter = new IRConverter(appView);
      ThreadUtils.processItems(
          classInitializers,
          method -> processMethod(method, converter),
          appView.options().getThreadingModule(),
          executorService);
      appView.dexItemFactory().clearTypeElementsCache();
    }
  }

  private void processMethod(ProgramMethod method, IRConverter converter) {
    IRCode code = method.buildIR(appView, MethodConversionOptions.forLirPhase(appView));
    converter.removeDeadCodeAndFinalizeIR(
        code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
  }

  public boolean isEmpty() {
    return classInitializers.isEmpty();
  }

  public static class Builder {

    private final AppView<?> appView;

    private final List<ProgramMethod> classInitializers = new ArrayList<>();

    private Builder(AppView<?> appView) {
      this.appView = appView;
    }

    public Builder addClassInitializer(ProgramMethod method) {
      this.classInitializers.add(method);
      return this;
    }

    public SyntheticInitializerConverter build() {
      return new SyntheticInitializerConverter(appView, classInitializers);
    }
  }
}
