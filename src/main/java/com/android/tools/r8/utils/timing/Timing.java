// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingAction;
import com.android.tools.r8.utils.ThrowingSupplier;
import java.util.concurrent.ExecutorService;

public abstract class Timing implements AutoCloseable {

  public static Timing empty() {
    return TimingEmpty.getEmpty();
  }

  public static Timing createRoot(
      String title, InternalOptions options, ExecutorService executorService) {
    if (options.partialSubCompilationConfiguration != null) {
      return options.partialSubCompilationConfiguration.timing;
    }
    return internalCreate(title, options, executorService);
  }

  private static Timing internalCreate(
      String title, InternalOptions options, ExecutorService executorService) {
    // We also create a timer when running assertions to validate wellformedness of the node stack.
    Timing timing;
    if (options.perfettoTraceDumpDirectory != null) {
      timing = new PerfettoTiming(title, options, executorService);
    } else if (options.printTimes) {
      timing = new TimingImpl(title, options);
    } else {
      timing = Timing.empty();
    }
    if (options.cancelCompilationChecker != null) {
      return new TimingWithCancellation(options, timing);
    }
    return timing;
  }

  public Timing createThreadTiming(String title, InternalOptions options) {
    return internalCreate(title, options, null);
  }

  public void notifyThreadTimingFinished() {
    // Intentionally empty.
  }

  public abstract Timing begin(String title);

  public abstract Timing end();

  public abstract TimingMerger beginMerger(String title, int numberOfThreads);

  public final TimingMerger beginMerger(String title, ExecutorService executorService) {
    return beginMerger(title, ThreadUtils.getNumberOfThreads(executorService));
  }

  public boolean isEmpty() {
    return false;
  }

  public abstract <E extends Exception> void time(String title, ThrowingAction<E> action) throws E;

  public abstract <T, E extends Exception> T time(String title, ThrowingSupplier<T, E> supplier)
      throws E;

  public abstract void report();

  // Remove throws from close() in AutoClosable to allow try with resources without explicit catch.
  @Override
  public final void close() {
    end();
  }
}
