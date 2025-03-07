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

  public static Timing createRoot(String title, InternalOptions options) {
    if (options.partialSubCompilationConfiguration != null) {
      return options.partialSubCompilationConfiguration.timing;
    }
    return create(title, options);
  }

  public static Timing create(String title, InternalOptions options) {
    // We also create a timer when running assertions to validate wellformedness of the node stack.
    Timing timing =
        options.printTimes || InternalOptions.assertionsEnabled()
            ? new TimingImpl(title, options)
            : Timing.empty();
    if (options.cancelCompilationChecker != null) {
      return new TimingWithCancellation(options, timing);
    }
    return timing;
  }

  public abstract Timing begin(String title);

  public abstract Timing end();

  public abstract TimingMerger beginMerger(String title, int numberOfThreads);

  public final TimingMerger beginMerger(String title, ExecutorService executorService) {
    return beginMerger(title, ThreadUtils.getNumberOfThreads(executorService));
  }

  public abstract <E extends Exception> void time(String title, ThrowingAction<E> action) throws E;

  public abstract <T, E extends Exception> T time(String title, ThrowingSupplier<T, E> supplier)
      throws E;

  public abstract void report();

  // Remove throws from close() in AutoClosable to allow try with resources without explicit catch.
  @Override
  public abstract void close();
}
