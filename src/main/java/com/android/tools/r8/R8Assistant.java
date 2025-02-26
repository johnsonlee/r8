// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.assistant.ReflectiveInstrumentation;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.ir.conversion.PrimaryD8L8IRConverter;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.synthesis.SyntheticFinalization;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * This is an experimental API for injecting reflective identification callbacks into dex code. This
 * API is subject to change.
 */
@KeepForApi
public class R8Assistant {

  public static void run(R8AssistantCommand command) throws CompilationFailedException {
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withCompilationHandler(
        options.reporter,
        () -> runInternal(command, options, ThreadUtils.getExecutorService(options)));
  }

  public static void run(R8AssistantCommand command, ExecutorService executor)
      throws CompilationFailedException {
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withD8CompilationHandler(
        command.getReporter(),
        () -> {
          runInternal(command, options, executor);
        });
  }

  static void runInternal(
      R8AssistantCommand command, InternalOptions options, ExecutorService executorService)
      throws IOException {
    Timing timing = new Timing("R8 Assistant " + Version.LABEL);
    try {
      ApplicationReader applicationReader =
          new ApplicationReader(command.getInputApp(), options, timing);
      LazyLoadedDexApplication app = applicationReader.read(executorService);
      assert !command.getInputApp().hasMainDexList();
      AppInfo appInfo =
          AppInfo.createInitialAppInfo(app, GlobalSyntheticsStrategy.forSingleOutputMode());
      AppView<AppInfo> appView = AppView.createForD8(appInfo);
      PrimaryD8L8IRConverter converter = new PrimaryD8L8IRConverter(appView, timing);
      ReflectiveInstrumentation reflectiveInstrumentation =
          new ReflectiveInstrumentation(appView, converter, timing);
      reflectiveInstrumentation.instrumentClasses();
      // Convert cf classes
      converter.convert(appView, executorService);
      if (command.getReflectiveReceiverDescriptor() != null) {
        reflectiveInstrumentation.updateReflectiveReceiver(
            command.getReflectiveReceiverDescriptor());
      }
      SyntheticFinalization.finalize(appView, timing, executorService);
      ApplicationWriter writer = ApplicationWriter.create(appView, options.getMarker());
      writer.write(executorService);
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } finally {
      options.signalFinishedToConsumers();
      if (options.isPrintTimesReportingEnabled()) {
        timing.report();
      }
    }
  }
}
