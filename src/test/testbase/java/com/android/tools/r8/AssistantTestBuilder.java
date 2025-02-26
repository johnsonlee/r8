// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestBase.descriptor;
import static com.android.tools.r8.TestBase.testForD8;
import static com.android.tools.r8.TestBase.writeClassesToJar;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AssistantTestBuilder
    extends TestCompilerBuilder<
        R8AssistantCommand,
        R8AssistantCommand.Builder,
        AssistantTestCompileResult,
        AssistantTestRunResult,
        AssistantTestBuilder> {

  private final D8TestBuilder initialCompileBuilder;
  private Path output;
  private String customReflectiveOperationReceiver = null;
  private List<Class<?>> customReflectiveOperationInputClasses = new ArrayList<>();

  private AssistantTestBuilder(TestState state) {
    super(state, R8AssistantCommand.builder(state.getDiagnosticsHandler()), Backend.DEX);
    initialCompileBuilder = testForD8(state.getTempFolder());
  }

  public static AssistantTestBuilder create(TestState state) {
    return new AssistantTestBuilder(state);
  }

  @Override
  AssistantTestBuilder self() {
    return this;
  }

  @Override
  public AssistantTestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    throw new Unimplemented("No classpath for assistant");
  }

  @Override
  public AssistantTestBuilder addClasspathFiles(Collection<Path> files) {
    throw new Unimplemented("No classpath for assistant");
  }

  public AssistantTestBuilder addInstrumentationClasses(Class<?>... classes) {
    Collections.addAll(customReflectiveOperationInputClasses, classes);
    return self();
  }

  @Override
  public AssistantTestBuilder addProgramFiles(Collection<Path> files) {
    initialCompileBuilder.addProgramFiles(files);
    return self();
  }

  @Override
  AssistantTestCompileResult internalCompile(
      R8AssistantCommand.Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<AndroidApp> app,
      BenchmarkResults benchmarkResults)
      throws CompilationFailedException {
    Path initialCompilation;
    try {
      initialCompilation = initialCompileBuilder.setMinApi(getMinApiLevel()).compile().writeToZip();
      if (output == null) {
        output = getState().getNewTempFile("assistant_output.jar");
      }
      if (!customReflectiveOperationInputClasses.isEmpty()) {
        builder.addReflectiveOperationReceiverInput(
            ArchiveProgramResourceProvider.fromArchive(
                writeClassesToJar(customReflectiveOperationInputClasses)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    builder
        .addProgramFiles(initialCompilation)
        .setOutput(output, OutputMode.DexIndexed)
        .setMinApiLevel(getMinApiLevel());

    if (customReflectiveOperationReceiver != null) {
      builder.setReflectiveReceiverClassDescriptor(customReflectiveOperationReceiver);
    }
    R8Assistant.run(builder.build());
    return new AssistantTestCompileResult(
        initialCompilation,
        getState(),
        AndroidApp.builder().addProgramFiles(output).build(),
        getMinApiLevel());
  }

  public AssistantTestBuilder setCustomReflectiveOperationReceiver(
      String customReflectiveOperationReceiver) {
    this.customReflectiveOperationReceiver = customReflectiveOperationReceiver;
    return self();
  }

  public AssistantTestBuilder setCustomReflectiveOperationReceiver(
      Class<?> customReflectiveOperationReceiver) {
    this.customReflectiveOperationReceiver = descriptor(customReflectiveOperationReceiver);
    return self();
  }
}
