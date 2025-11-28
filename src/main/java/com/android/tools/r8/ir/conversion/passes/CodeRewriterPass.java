// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;

public abstract class CodeRewriterPass<T extends AppInfo> {

  public static final CodeRewriterPass<?>[] EMPTY_ARRAY = new CodeRewriterPass[0];

  protected final AppView<?> appView;
  protected final DexItemFactory dexItemFactory;
  protected final InternalOptions options;

  protected CodeRewriterPass(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
  }

  @SuppressWarnings("unchecked")
  protected AppView<T> appView() {
    return (AppView<T>) appView;
  }

  protected T appInfo() {
    return appView().appInfo();
  }

  public final CodeRewriterResult run(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Timing timing) {
    return timing.time(getRewriterId(), () -> run(code, methodProcessor, methodProcessingContext));
  }

  @Deprecated
  public final CodeRewriterResult run(IRCode code, Timing timing) {
    return timing.time(getRewriterId(), () -> run(code, null, null));
  }

  private CodeRewriterResult run(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    if (shouldRewriteCode(code, methodProcessor)) {
      assert verifyConsistentCode(code, isAcceptingSSA(), "before");
      CodeRewriterResult result = rewriteCode(code, methodProcessor, methodProcessingContext);
      assert result.hasChanged().isFalse() || verifyConsistentCode(code, isProducingSSA(), "after");
      return result;
    }
    return noChange();
  }

  protected boolean verifyConsistentCode(IRCode code, boolean ssa, String preposition) {
    boolean result;
    String message = "Invalid code " + preposition + " " + getRewriterId();
    try {
      result = ssa ? code.isConsistentSSA(appView) : code.isConsistentGraph(appView, false);
    } catch (AssertionError ae) {
      throw new AssertionError(message, ae);
    }
    assert result : message;
    return true;
  }

  protected CodeRewriterResult noChange() {
    return CodeRewriterResult.NO_CHANGE;
  }

  protected boolean isDebugMode(ProgramMethod context) {
    return options.debug || context.isReachabilitySensitive();
  }

  protected abstract String getRewriterId();

  protected boolean isAcceptingSSA() {
    return true;
  }

  protected boolean isProducingSSA() {
    return true;
  }

  protected CodeRewriterResult rewriteCode(IRCode code) {
    throw new Unreachable("Should Override or use overload");
  }

  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    return rewriteCode(code);
  }

  protected abstract boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor);
}
