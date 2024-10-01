// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.metadata.R8BuildMetadata;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class R8PartialTestCompileResult
    extends R8TestCompileResultBase<R8PartialTestCompileResult> {

  private final AndroidApp r8InputApp;
  private final AndroidApp d8InputApp;
  private final AndroidApp r8OutputApp;
  private final AndroidApp d8OutputApp;

  R8PartialTestCompileResult(
      TestState state,
      OutputMode outputMode,
      LibraryDesugaringTestConfiguration libraryDesugaringTestConfiguration,
      AndroidApp app,
      String proguardConfiguration,
      List<ProguardConfigurationRule> syntheticProguardRules,
      String proguardMap,
      CollectingGraphConsumer graphConsumer,
      int minApiLevel,
      List<Path> features,
      List<ExternalArtProfile> residualArtProfiles,
      Path resourceShrinkerOutput,
      HashMap<String, Path> resourceShrinkerOutputForFeatures,
      R8BuildMetadata buildMetadata,
      AndroidApp r8InputApp,
      AndroidApp d8InputApp,
      AndroidApp r8OutputApp,
      AndroidApp d8OutputApp) {
    super(
        state,
        outputMode,
        libraryDesugaringTestConfiguration,
        app,
        proguardConfiguration,
        syntheticProguardRules,
        proguardMap,
        graphConsumer,
        minApiLevel,
        features,
        residualArtProfiles,
        resourceShrinkerOutput,
        resourceShrinkerOutputForFeatures,
        buildMetadata);
    this.r8InputApp = r8InputApp;
    this.d8InputApp = d8InputApp;
    this.r8OutputApp = r8OutputApp;
    this.d8OutputApp = d8OutputApp;
  }

  @Override
  public R8PartialTestCompileResult self() {
    return this;
  }

  public CodeInspector inspectorR8Input() throws IOException {
    return new CodeInspector(r8InputApp);
  }

  public CodeInspector inspectorR8Input(Consumer<InternalOptions> debugOptionsConsumer)
      throws IOException {
    return new CodeInspector(r8InputApp, debugOptionsConsumer);
  }

  public <E extends Throwable> R8PartialTestCompileResult inspectR8Input(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    consumer.accept(inspectorR8Input());
    return self();
  }

  public CodeInspector inspectorD8Input() throws IOException {
    return new CodeInspector(d8InputApp);
  }

  public CodeInspector inspectorD8Input(Consumer<InternalOptions> debugOptionsConsumer)
      throws IOException {
    return new CodeInspector(d8InputApp, debugOptionsConsumer);
  }

  public <E extends Throwable> R8PartialTestCompileResult inspectD8Input(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    consumer.accept(inspectorD8Input());
    return self();
  }

  public CodeInspector inspectorR8Output() throws IOException {
    return new CodeInspector(r8OutputApp);
  }

  public CodeInspector inspectorR8Output(Consumer<InternalOptions> debugOptionsConsumer)
      throws IOException {
    return new CodeInspector(r8OutputApp, debugOptionsConsumer);
  }

  public <E extends Throwable> R8PartialTestCompileResult inspectR8Output(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    consumer.accept(inspectorR8Output());
    return self();
  }

  public CodeInspector inspectorD8Output() throws IOException {
    return new CodeInspector(d8OutputApp);
  }

  public CodeInspector inspectorD8Output(Consumer<InternalOptions> debugOptionsConsumer)
      throws IOException {
    return new CodeInspector(d8OutputApp, debugOptionsConsumer);
  }

  public <E extends Throwable> R8PartialTestCompileResult inspectD8Output(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    consumer.accept(inspectorD8Output());
    return self();
  }
}
