// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Assistant;
import com.android.tools.r8.R8AssistantCommand;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wrapper to make it easy to call R8 Assistant mode when compiling a dump file.
 *
 * <p>This wrapper will be added to the classpath so it *must* only refer to the public API. See
 * {@code tools/assistant/instrument_with_assistant_and_run_test.py}.
 */
public class CompileDumpR8Assistant extends CompileDumpBase {

  private static final List<String> VALID_OPTIONS = Arrays.asList("--reflective-usage-json-output");

  private static final List<String> VALID_OPTIONS_WITH_SINGLE_OPERAND =
      Arrays.asList("--output", "--lib", "--classpath", "--min-api", "--threads");

  public static void main(String[] args) throws CompilationFailedException {
    Path outputPath = null;
    CompilationMode compilationMode = CompilationMode.RELEASE;
    List<Path> program = new ArrayList<>();
    List<Path> library = new ArrayList<>();
    List<Path> classpath = new ArrayList<>();
    int minApi = 1;
    int threads = -1;
    boolean reflectiveUsageAsJson = false;

    for (int i = 0; i < args.length; i++) {
      String option = args[i];
      if (VALID_OPTIONS.contains(option)) {
        switch (option) {
          case "--reflective-usage-json-output":
            reflectiveUsageAsJson = true;
            break;
          default:
            throw new IllegalArgumentException("Unimplemented option: " + option);
        }
      } else if (VALID_OPTIONS_WITH_SINGLE_OPERAND.contains(option)) {
        String operand = args[++i];
        switch (option) {
          case "--output":
            {
              outputPath = Paths.get(operand);
              break;
            }
          case "--lib":
            {
              library.add(Paths.get(operand));
              break;
            }
          case "--classpath":
            {
              classpath.add(Paths.get(operand));
              break;
            }
          case "--min-api":
            {
              minApi = Integer.parseInt(operand);
              break;
            }
          case "--threads":
            {
              threads = Integer.parseInt(operand);
              break;
            }
          default:
            throw new IllegalArgumentException("Unimplemented option: " + option);
        }
      } else {
        program.add(Paths.get(option));
      }
    }
    R8AssistantCommand.Builder commandBuilder =
        R8AssistantCommand.builder()
            .addProgramFiles(program)
            .addLibraryFiles(library)
            .addClasspathFiles(classpath)
            .setOutput(outputPath, OutputMode.DexIndexed)
            .setMode(compilationMode)
            .setMinApiLevel(minApi);
    if (reflectiveUsageAsJson) {
      commandBuilder.setReflectiveReceiverClass(ReflectiveOperationJsonLogger.class);
    }
    R8AssistantCommand command = commandBuilder.build();
    if (threads != -1) {
      ExecutorService executor = Executors.newWorkStealingPool(threads);
      try {
        R8Assistant.run(command, executor);
      } finally {
        executor.shutdown();
      }
    } else {
      R8Assistant.run(command);
    }
  }
}
