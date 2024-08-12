// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.IntraproceduralDataflowAnalysis;
import com.android.tools.r8.ir.analysis.path.state.PathConstraintAnalysisState;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * An analysis that computes the set of active path constraints for each program point. Each path
 * constraint is an expression where only the arguments of the current method are allowed as open
 * variables.
 *
 * <p>Consider the following example.
 *
 * <pre>
 *   static Object Foo(Object o, int flags) {
 *     if ((flags & 1) != 0) {
 *       o = DEFAULT_VALUE;
 *     }
 *     return o;
 *   }
 * </pre>
 *
 * <ul>
 *   <li>The path constraint for the entry block is the empty set, since control flow
 *       unconditionally hits this block.
 *   <li>The path constraint for the block containing {@code o = DEFAULT_VALUE} is [(flags & 1) !=
 *       0].
 *   <li>The path constraint for the (empty) ELSE block is [(flags & 1) == 0].
 *   <li>The path constraint for the return block is the empty set.
 * </ul>
 */
public class PathConstraintAnalysis
    extends IntraproceduralDataflowAnalysis<PathConstraintAnalysisState> {

  public PathConstraintAnalysis(AppView<AppInfoWithLiveness> appView, IRCode code) {
    super(
        appView,
        PathConstraintAnalysisState.bottom(),
        code,
        new PathConstraintAnalysisTransferFunction(appView.abstractValueFactory()));
  }
}
