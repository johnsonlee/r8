// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.compiledump;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileProviderUtils;
import java.nio.file.Path;

public class ArtProfileDumpUtils {
  public static void addArtProfileForRewriting(
      Path input, Path output, BaseCompilerCommand.Builder<?, ?> builder) {
    builder.addArtProfileForRewriting(
        ArtProfileProviderUtils.createFromHumanReadableArtProfile(input),
        ArtProfileConsumerUtils.create(output));
  }
}
