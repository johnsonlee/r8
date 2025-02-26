// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.assistant.ClassInjectionHelper;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle.ReflectiveOperationLogger;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Backend;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.Collections;

/**
 * This is an experimental API for injecting reflective identification callbacks into dex code. This
 * API is subject to change.
 */
@KeepForApi
public class R8AssistantCommand extends BaseCompilerCommand {

  private final String reflectiveReceiverDescriptor;

  public R8AssistantCommand(
      AndroidApp app,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      int minApiLevel,
      Reporter reporter,
      String reflectiveReceiverDescriptor) {
    super(
        app,
        mode,
        programConsumer,
        StringConsumer.emptyConsumer(),
        minApiLevel,
        reporter,
        DesugarState.ON,
        false,
        false,
        (a, b) -> true,
        Collections.emptyList(),
        Collections.emptyList(),
        ThreadUtils.NOT_SPECIFIED,
        DumpInputFlags.noDump(),
        null,
        null,
        false,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null);
    this.reflectiveReceiverDescriptor = reflectiveReceiverDescriptor;
  }

  public static Builder builder(DiagnosticsHandler reporter) {
    return new Builder(reporter);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  InternalOptions getInternalOptions() {
    DexItemFactory factory = new DexItemFactory();
    InternalOptions options = new InternalOptions(factory, getReporter());
    options.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(getMinApiLevel()));
    options.passthroughDexCode = true;
    options.tool = Tool.R8Assistant;
    Marker marker = new Marker(Tool.R8Assistant);
    marker.setBackend(Backend.DEX);
    marker.setMinApi(getMinApiLevel());
    options.setMarker(marker);
    options.programConsumer = getProgramConsumer();
    return options;
  }

  public String getReflectiveReceiverDescriptor() {
    return reflectiveReceiverDescriptor;
  }

  /**
   * This is an experimental API for injecting reflective identification callbacks into dex code.
   * This API is subject to change.
   */
  @KeepForApi
  public static class Builder extends BaseCompilerCommand.Builder<R8AssistantCommand, Builder> {

    private String reflectiveReceiverDescriptor;

    private Builder() {
      this(new DiagnosticsHandler() {});
    }

    @Override
    void validate() {
      if (!(getProgramConsumer() instanceof DexIndexedConsumer)) {
        getReporter().error("R8 assistant does not support CF output.");
      }
      if (!hasNativeMultidex()) {
        getReporter().error("R8 assistant requires min api >= 21");
      }
    }

    public Builder addReflectiveOperationReceiverInput(ProgramResourceProvider provider) {
      // This code is simply added to the program input, will be called by the ReflectiveOracle.
      addProgramResourceProvider(provider);
      return self();
    }

    public Builder setReflectiveReceiverClassDescriptor(String descriptor) {
      if (!DescriptorUtils.isClassDescriptor(descriptor)) {
        getReporter().error("Not a valid descriptor " + descriptor);
      }
      this.reflectiveReceiverDescriptor = descriptor;
      return self();
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.RELEASE;
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    R8AssistantCommand makeCommand() {
      ClassInjectionHelper injectionHelper = new ClassInjectionHelper(getReporter());
      String reason = "Reflective instrumentation";
      addClassProgramData(
          injectionHelper.getClassBytes(ReflectiveOracle.class),
          new SynthesizedOrigin(reason, ReflectiveOracle.class));
      addClassProgramData(
          injectionHelper.getClassBytes(Stack.class), new SynthesizedOrigin(reason, Stack.class));
      addClassProgramData(
          injectionHelper.getClassBytes(ReflectiveOperationReceiver.class),
          new SynthesizedOrigin(reason, ReflectiveOperationReceiver.class));
      addClassProgramData(
          injectionHelper.getClassBytes(ReflectiveOperationLogger.class),
          new SynthesizedOrigin(reason, ReflectiveOperationLogger.class));
      return new R8AssistantCommand(
          getAppBuilder().build(),
          getMode(),
          getProgramConsumer(),
          getMinApiLevel(),
          getReporter(),
          reflectiveReceiverDescriptor);
    }
  }
}
