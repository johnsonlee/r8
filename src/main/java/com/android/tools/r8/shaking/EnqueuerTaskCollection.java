// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FinishedEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.FixpointEnqueuerAnalysis;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.timing.Timing;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class EnqueuerTaskCollection implements FixpointEnqueuerAnalysis, FinishedEnqueuerAnalysis {

  // A task collection that stores tasks that may enqueue worklist items. The enqueuer therefore
  // needs to await this task collection upon each intermediate fixpoint.
  private final TaskCollection<Void> enqueuerDependentTaskCollection;

  // A task collection that stores tasks that the Enqueuer does not need to await until it is
  // entirely done.
  private final TaskCollection<Void> enqueuerIndependentTaskCollection;

  EnqueuerTaskCollection(ThreadingModule threadingModule, ExecutorService executorService) {
    this.enqueuerDependentTaskCollection = new TaskCollection<>(threadingModule, executorService);
    this.enqueuerIndependentTaskCollection = new TaskCollection<>(threadingModule, executorService);
  }

  public void register(EnqueuerAnalysisCollection.Builder analysesBuilder) {
    analysesBuilder.addFixpointAnalysis(this).addFinishedAnalysis(this);
  }

  public void submitEnqueuerDependentTask(Callable<Void> callable) {
    enqueuerDependentTaskCollection.submitUnchecked(callable);
  }

  public void submitEnqueuerIndependentTask(Callable<Void> callable) {
    enqueuerIndependentTaskCollection.submitUnchecked(callable);
  }

  public void awaitEnqueuerIndependentTasks() throws ExecutionException {
    enqueuerIndependentTaskCollection.await();
  }

  /** Returns true if all Enqueuer dependent tasks were removed due to their completion. */
  public boolean removeCompletedEnqueuerDependentTasks() {
    enqueuerDependentTaskCollection.removeCompletedFutures();
    return enqueuerDependentTaskCollection.isEmpty();
  }

  @Override
  public void notifyFixpoint(
      Enqueuer enqueuer, EnqueuerWorklist worklist, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    enqueuerDependentTaskCollection.await();
    enqueuerIndependentTaskCollection.removeCompletedFutures();
  }

  @Override
  public void done(Enqueuer enqueuer, ExecutorService executorService) {
    assert enqueuerDependentTaskCollection.isEmpty();
    assert enqueuerIndependentTaskCollection.isEmpty();
  }
}
