// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.utils.Timing;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public interface FixpointEnqueuerAnalysis {

  /**
   * Called when the Enqueuer reaches a fixpoint. This may happen multiple times, since each
   * analysis may enqueue items into the worklist upon the fixpoint using {@param worklist}.
   */
  default void notifyFixpoint(
      Enqueuer enqueuer, EnqueuerWorklist worklist, ExecutorService executorService, Timing timing)
      throws ExecutionException {}
}
