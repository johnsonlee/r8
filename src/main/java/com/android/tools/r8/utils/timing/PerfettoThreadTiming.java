// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import androidx.tracing.driver.ThreadTrack;
import com.android.tools.r8.errors.Unreachable;

class PerfettoThreadTiming extends TimingImplBase {

  private final int threadId;
  private final ThreadTrack threadTrack;

  PerfettoThreadTiming(int threadId, ThreadTrack threadTrack) {
    this.threadTrack = threadTrack;
    this.threadId = threadId;
  }

  @Override
  public Timing begin(String title) {
    assert threadId == Thread.currentThread().getId();
    threadTrack.beginSection(title);
    return this;
  }

  @Override
  public Timing end() {
    threadTrack.endSection();
    return this;
  }

  @Override
  public TimingMerger beginMerger(String title, int numberOfThreads) {
    throw new Unreachable();
  }

  @Override
  public void report() {
    throw new Unreachable();
  }
}
