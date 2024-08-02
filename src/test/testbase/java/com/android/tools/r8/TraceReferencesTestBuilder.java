// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.tracereferences.TraceReferences;
import com.android.tools.r8.tracereferences.TraceReferencesCommand;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.io.IOException;

public class TraceReferencesTestBuilder {

  private final TraceReferencesCommand.Builder builder;
  private final TraceReferencesInspector inspector = new TraceReferencesInspector();
  private final TestState state;

  public TraceReferencesTestBuilder(TestState state) {
    this.builder =
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .setConsumer(inspector);
    this.state = state;
  }

  public TraceReferencesTestBuilder addInnerClassesAsSourceClasses(Class<?> clazz)
      throws IOException {
    builder.addSourceFiles(
        ZipBuilder.builder(state.getNewTempFolder().resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFilesForInnerClasses(clazz))
            .build());
    return this;
  }

  public TraceReferencesTestBuilder addInnerClassesAsTargetClasses(Class<?> clazz)
      throws IOException {
    builder.addTargetFiles(
        ZipBuilder.builder(state.getNewTempFolder().resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFilesForInnerClasses(clazz))
            .build());
    return this;
  }

  public TraceReferencesTestResult trace() throws CompilationFailedException {
    TraceReferences.run(builder.build());
    return new TraceReferencesTestResult(inspector);
  }
}
