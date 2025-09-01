// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingAction;
import com.android.tools.r8.utils.ThrowingSupplier;

class TimingEmpty extends Timing {

  private static final TimingEmpty INSTANCE = new TimingEmpty();

  static Timing getEmpty() {
    return INSTANCE;
  }

  private TimingEmpty() {}

  @Override
  public TimingMerger beginMerger(String title, int numberOfThreads) {
    return EmptyTimingMerger.get();
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    return this;
  }

  @Override
  public Timing begin(String title) {
    return this;
  }

  @Override
  public Timing end() {
    return this;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public <E extends Exception> void time(String title, ThrowingAction<E> action) throws E {
    action.execute();
  }

  @Override
  public <T, E extends Exception> T time(String title, ThrowingSupplier<T, E> supplier) throws E {
    return supplier.get();
  }

  @Override
  public void report() {}
}
