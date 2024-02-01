// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.RedundantFieldLoadAndStoreElimination;
import com.android.tools.r8.ir.optimize.enums.EnumValueOptimizer;
import com.android.tools.r8.ir.optimize.string.StringBuilderAppendOptimizer;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CodeRewriterPassCollection {

  private final List<CodeRewriterPass<?>> passes;

  public CodeRewriterPassCollection(CodeRewriterPass<?>... passes) {
    this(Arrays.asList(passes));
  }

  public CodeRewriterPassCollection(List<CodeRewriterPass<?>> passes) {
    this.passes = passes;
  }

  public static CodeRewriterPassCollection create(AppView<?> appView) {
    List<CodeRewriterPass<?>> passes = new ArrayList<>();
    passes.add(new TrivialCheckCastAndInstanceOfRemover(appView));
    passes.add(new EnumValueOptimizer(appView));
    passes.add(new KnownArrayLengthRewriter(appView));
    passes.add(new NaturalIntLoopRemover(appView));
    passes.add(new CommonSubexpressionElimination(appView));
    passes.add(new ArrayConstructionSimplifier(appView));
    passes.add(new MoveResultRewriter(appView));
    passes.add(new StringBuilderAppendOptimizer(appView));
    passes.add(new SparseConditionalConstantPropagation(appView));
    passes.add(new ThrowCatchOptimizer(appView));
    passes.add(new BranchSimplifier(appView));
    passes.add(new SplitBranch(appView));
    passes.add(new RedundantConstNumberRemover(appView));
    if (!appView.options().debug) {
      passes.add(new RedundantFieldLoadAndStoreElimination(appView));
    }
    passes.add(new BinopRewriter(appView));
    return new CodeRewriterPassCollection(passes);
  }

  public boolean run(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Timing timing) {
    boolean changed = false;
    for (CodeRewriterPass<?> pass : passes) {
      // TODO(b/286345542): Run printMethod after each run.
      CodeRewriterResult result = pass.run(code, methodProcessor, methodProcessingContext, timing);
      changed |= result.hasChanged().isTrue();
    }
    return changed;
  }
}
