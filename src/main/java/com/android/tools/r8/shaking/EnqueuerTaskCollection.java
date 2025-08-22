// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.threading.ThreadingModule;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class EnqueuerTaskCollection {

  // A task collection that stores tasks that the Enqueuer does not need to await until it is
  // entirely done.
  private final TaskCollection<Void> enqueuerIndependentTaskCollection;

  EnqueuerTaskCollection(ThreadingModule threadingModule, ExecutorService executorService) {
    this.enqueuerIndependentTaskCollection = new TaskCollection<>(threadingModule, executorService);
  }

  public void submitEnqueuerIndependentTask(Callable<Void> callable) {
    enqueuerIndependentTaskCollection.submitUnchecked(callable);
  }

  public void awaitEnqueuerIndependentTasks() throws ExecutionException {
    enqueuerIndependentTaskCollection.await();
  }
}
