// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.singlecaller;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.ArrayDeque;
import java.util.Deque;

public class SingleCallerWaves {

  public static Deque<ProgramMethodSet> buildWaves(
      AppView<AppInfoWithLiveness> appView, ProgramMethodMap<ProgramMethod> singleCallerMethods) {
    SingleCallerInlinerCallGraph callGraph =
        SingleCallerInlinerCallGraph.builder(appView)
            .populateGraph(singleCallerMethods)
            .eliminateCycles()
            .build();
    Deque<ProgramMethodSet> waves = new ArrayDeque<>();
    // Intentionally drop first round of leaves as they are the callees.
    callGraph.extractLeaves();
    while (!callGraph.isEmpty()) {
      waves.addLast(callGraph.extractLeaves());
    }
    return waves;
  }
}
