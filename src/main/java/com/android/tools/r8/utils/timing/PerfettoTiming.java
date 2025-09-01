// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import androidx.tracing.driver.ProcessTrack;
import androidx.tracing.driver.ThreadTrack;
import androidx.tracing.driver.TraceContext;
import androidx.tracing.driver.TraceDriver;
import androidx.tracing.driver.wire.TraceSink;
import androidx.tracing.driver.wire.TraceSinkUtils;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.InternalOptions;
import java.io.File;

public class PerfettoTiming extends TimingImplBase {

  private final TraceSink sink;
  private final TraceDriver traceDriver;
  private final ProcessTrack processTrack;
  private final ThreadTrack threadTrack;

  private int depth = 0;

  public PerfettoTiming(String title, InternalOptions options) {
    if (options.printMemory) {
      throw new Unimplemented();
    }
    File directory = new File(options.perfettoTraceDumpDirectory);
    int sequenceId = 1;
    sink = TraceSinkUtils.TraceSink(directory, sequenceId);
    traceDriver = new TraceDriver(sink, true);
    TraceContext traceContext = traceDriver.getContext();
    processTrack = traceContext.getOrCreateProcessTrack(1, title);
    int mainThreadId = (int) Thread.currentThread().getId();
    threadTrack = processTrack.getOrCreateThreadTrack(mainThreadId, "Main thread");
    begin(title);
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    int threadId = (int) Thread.currentThread().getId();
    ThreadTrack threadTrack = processTrack.getOrCreateThreadTrack(threadId, "Worker");
    return new PerfettoThreadTiming(threadTrack).begin(title);
  }

  @Override
  public Timing begin(String title) {
    threadTrack.beginSection(title);
    depth++;
    return this;
  }

  @Override
  public Timing end() {
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
    end();
    assert depth == 0 : depth;
    traceDriver.getContext().flush();
    sink.close();
  }
}
