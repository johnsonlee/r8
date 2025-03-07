// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import com.android.tools.r8.utils.ThrowingAction;
import com.android.tools.r8.utils.ThrowingSupplier;

class TimingDelegate extends Timing {

  private final Timing delegate;

  public TimingDelegate(Timing timing) {
    this.delegate = timing;
  }

  @Override
  public TimingMerger beginMerger(String title, int numberOfThreads) {
    return delegate.beginMerger(title, numberOfThreads);
  }

  @Override
  public Timing begin(String title) {
    delegate.begin(title);
    return this;
  }

  @Override
  public <E extends Exception> void time(String title, ThrowingAction<E> action) throws E {
    delegate.time(title, action);
  }

  @Override
  public <T, E extends Exception> T time(String title, ThrowingSupplier<T, E> supplier) throws E {
    return delegate.time(title, supplier);
  }

  @Override
  public Timing end() {
    delegate.end();
    return this;
  }

  @Override
  public void report() {
    delegate.report();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
