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

public class AwaitableFutureValue<T> implements AwaitableValue<T> {

  private final Future<T> future;

  public static <T> AwaitableFutureValue<T> create(Future<T> future) {
    return new AwaitableFutureValue<>(future);
  }

  public static <T> AwaitableFutureValue<T> create(
      Callable<T> task, ThreadingModule threadingModule, ExecutorService executorService)
      throws ExecutionException {
    return create(threadingModule.submit(task, executorService));
  }

  public static <T> AwaitableFutureValue<T> createCompleted(T value) {
    return new AwaitableFutureValue<>(Futures.immediateFuture(value));
  }

  public AwaitableFutureValue(Future<T> future) {
    assert future != null;
    this.future = future;
  }

  @Override
  public T awaitValue() throws ExecutionException {
    try {
      return future.get();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted waiting for future", e);
    }
  }
}
