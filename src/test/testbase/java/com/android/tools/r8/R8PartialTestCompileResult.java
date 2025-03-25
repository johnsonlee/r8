// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.androidresources.DebugConsumerUtils.TestDebugConsumer;
import com.android.tools.r8.metadata.R8BuildMetadata;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class R8PartialTestCompileResult
    extends R8TestCompileResultBase<R8PartialTestCompileResult> {

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
      TestDebugConsumer resourceShrinkerLogConsumer,
      R8BuildMetadata buildMetadata) {
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
        resourceShrinkerLogConsumer,
        buildMetadata);
  }

  @Override
  public boolean isR8PartialCompileResult() {
    return true;
  }

  @Override
  public R8PartialTestCompileResult asR8PartialCompileResult() {
    return this;
  }

  @Override
  public R8PartialTestCompileResult self() {
    return this;
  }
}
