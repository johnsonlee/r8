// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import androidx.tracing.driver.CounterTrack;
import androidx.tracing.driver.ProcessTrack;
import androidx.tracing.driver.ThreadTrack;
import androidx.tracing.driver.TraceContext;
import androidx.tracing.driver.TraceDriver;
import androidx.tracing.driver.wire.TraceSink;
import androidx.tracing.driver.wire.TraceSinkUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class PerfettoTiming extends TimingImplBase {

  private final TraceSink sink;
  private final TraceDriver traceDriver;
  private final ProcessTrack processTrack;
  private final ThreadTrack threadTrack;

  private int depth = 0;
  private CounterTrack memoryTrack;
  private Future<Void> memoryTracker;
  private volatile boolean memoryTrackerActive = true;

  public PerfettoTiming(String title, InternalOptions options, ExecutorService executorService) {
    File directory = new File(options.perfettoTraceDumpDirectory);
    int sequenceId = 1;
    sink = TraceSinkUtils.TraceSink(directory, sequenceId);
    traceDriver = new TraceDriver(sink, true);
    TraceContext traceContext = traceDriver.getContext();
    processTrack = traceContext.getOrCreateProcessTrack(1, title);
    int mainThreadId = (int) Thread.currentThread().getId();
    threadTrack = processTrack.getOrCreateThreadTrack(mainThreadId, "Main thread");
    begin(title);
    // Memory tracking requires an executor service.
    if (executorService != null) {
      memoryTrack = processTrack.getOrCreateCounterTrack("Memory");
      memoryTracker =
          executorService.submit(
              () -> {
                while (true) {
                  // Check the memoryCounterActive flag every 250ms.
                  for (int i = 0; i < 4; i++) {
                    Thread.sleep(250);
                    if (!memoryTrackerActive) {
                      return null;
                    }
                  }
                  // Update the memory counter every 1s.
                  memoryTrack.setCounter(
                      Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                }
              });
    }
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    int threadId = (int) Thread.currentThread().getId();
    ThreadTrack threadTrack = processTrack.getOrCreateThreadTrack(threadId, "Worker");
    return new PerfettoThreadTiming(threadTrack).begin(title);
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
    end();
    assert depth == 0 : depth;
    awaitMemoryTracker();
    traceDriver.getContext().flush();
    sink.close();
  }

  private void awaitMemoryTracker() {
    if (memoryTracker != null) {
      // Signal to the memory tracker to stop.
      memoryTrackerActive = false;
      // Await the memory tracker.
      try {
        memoryTracker.get();
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while waiting for future.", e);
      } catch (ExecutionException e) {
        throw unwrapExecutionException(e);
      }
      memoryTrack = null;
      memoryTracker = null;
    }
  }
}
