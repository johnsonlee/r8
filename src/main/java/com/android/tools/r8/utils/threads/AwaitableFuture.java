// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.threads;

import com.android.tools.r8.threading.ThreadingModule;
import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class AwaitableFuture implements Awaitable {

  private final Future<Void> future;

  public static AwaitableFuture create(Future<Void> future) {
    return new AwaitableFuture(future);
  }

  public static AwaitableFuture create(
      Callable<Void> task, ThreadingModule threadingModule, ExecutorService executorService)
      throws ExecutionException {
    return create(threadingModule.submit(task, executorService));
  }

  public static AwaitableFuture createCompleted() {
    return new AwaitableFuture(Futures.immediateFuture(null));
  }

  public AwaitableFuture(Future<Void> future) {
    assert future != null;
    this.future = future;
  }

  @Override
  public void await() throws ExecutionException {
    try {
      future.get();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted waiting for future", e);
    }
  }
}
