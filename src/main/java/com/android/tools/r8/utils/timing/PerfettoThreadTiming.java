// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import androidx.tracing.driver.ThreadTrack;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.InternalOptions;

class PerfettoThreadTiming extends TimingImplBase {

  private final ThreadTrack threadTrack;

  private int depth = 0;

  PerfettoThreadTiming(ThreadTrack threadTrack) {
    this.threadTrack = threadTrack;
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    int threadId = (int) Thread.currentThread().getId();
    ThreadTrack newThreadTrack =
        threadTrack.getProcess().getOrCreateThreadTrack(threadId, "Worker");
    return new PerfettoThreadTiming(newThreadTrack).begin(title);
  }

  @Override
  public void notifyThreadTimingFinished() {
    assert depth == 0;
  }

  @Override
  public Timing begin(String title) {
    assert threadTrack.getId() == Thread.currentThread().getId();
    threadTrack.beginSection(title);
    depth++;
    return this;
  }

  @Override
  public Timing end() {
    assert threadTrack.getId() == Thread.currentThread().getId();
    threadTrack.endSection();
    depth--;
    return this;
  }

  @Override
  public TimingMerger beginMerger(String title, int numberOfThreads) {
    return EmptyTimingMerger.get();
  }

  @Override
  public void report() {
    throw new Unreachable();
  }
}
