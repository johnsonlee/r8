// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration;

public abstract class MethodConversionOptions {

  public static MutableMethodConversionOptions forPreLirPhase(AppView<?> appView) {
    if (!appView.enableWholeProgramOptimizations()) {
      return forD8(appView);
    }
    assert appView.testing().isPreLirPhase();
    return new MutableMethodConversionOptions(Target.CF);
  }

  public static MutableMethodConversionOptions forPostLirPhase(AppView<?> appView) {
    assert appView.testing().isPostLirPhase();
    Target target = appView.options().isGeneratingClassFiles() ? Target.CF : Target.DEX;
    return new MutableMethodConversionOptions(target);
  }

  public static MutableMethodConversionOptions forLirPhase(AppView<?> appView) {
    if (!appView.enableWholeProgramOptimizations()) {
      return forD8(appView);
    }
    assert appView.testing().isSupportedLirPhase();
    return new MutableMethodConversionOptions(determineTarget(appView, null));
  }

  public static MutableMethodConversionOptions forD8(AppView<?> appView) {
    return forD8(appView, null);
  }

  public static MutableMethodConversionOptions forD8(AppView<?> appView, ProgramMethod method) {
    assert !appView.enableWholeProgramOptimizations();
    return new MutableMethodConversionOptions(determineTarget(appView, method));
  }

  public static MutableMethodConversionOptions nonConverting() {
    return new ThrowingMethodConversionOptions();
  }

  public IRFinalizer<?> getFinalizer(DeadCodeRemover deadCodeRemover, AppView<?> appView) {
    if (isGeneratingLir()) {
      return new IRToLirFinalizer(appView);
    }
    if (isGeneratingClassFiles()) {
      return new IRToCfFinalizer(appView, deadCodeRemover);
    }
    assert isGeneratingDex();
    return new IRToDexFinalizer(appView, deadCodeRemover);
  }

  public enum Target {
    CF,
    DEX,
    LIR
  }

  private static Target determineTarget(AppView<?> appView, ProgramMethod method) {
    R8PartialSubCompilationConfiguration subCompilationConfiguration =
        appView.options().partialSubCompilationConfiguration;
    if (subCompilationConfiguration != null && subCompilationConfiguration.isD8()) {
      return subCompilationConfiguration.asD8().getTargetFor(method, appView);
    }
    if (appView.testing().canUseLir(appView)) {
      return Target.LIR;
    }
    if (appView.options().isGeneratingClassFiles()) {
      return Target.CF;
    }
    assert appView.options().isGeneratingDex();
    return Target.DEX;
  }

  public abstract boolean isGeneratingLir();

  public abstract boolean isGeneratingClassFiles();

  public abstract boolean isGeneratingDex();

  public abstract boolean shouldFinalizeAfterLensCodeRewriter();

  public static class MutableMethodConversionOptions extends MethodConversionOptions {

    private final Target target;
    private boolean finalizeAfterLensCodeRewriter;

    private MutableMethodConversionOptions(Target target) {
      this.target = target;
    }

    public MutableMethodConversionOptions setFinalizeAfterLensCodeRewriter() {
      finalizeAfterLensCodeRewriter = true;
      return this;
    }

    @Override
    public boolean isGeneratingLir() {
      return target == Target.LIR;
    }

    @Override
    public boolean isGeneratingClassFiles() {
      return target == Target.CF;
    }

    @Override
    public boolean isGeneratingDex() {
      return target == Target.DEX;
    }

    @Override
    public boolean shouldFinalizeAfterLensCodeRewriter() {
      return finalizeAfterLensCodeRewriter;
    }
  }

  public static class ThrowingMethodConversionOptions extends MutableMethodConversionOptions {

    private ThrowingMethodConversionOptions() {
      super(null);
    }

    @Override
    public boolean isGeneratingLir() {
      throw new Unreachable();
    }

    @Override
    public boolean isGeneratingClassFiles() {
      throw new Unreachable();
    }

    @Override
    public boolean isGeneratingDex() {
      throw new Unreachable();
    }
  }
}
