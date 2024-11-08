// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.compiledump;

import com.android.tools.r8.ArchiveProtoAndroidResourceConsumer;
import com.android.tools.r8.ArchiveProtoAndroidResourceProvider;
import com.android.tools.r8.R8Command;
import java.nio.file.Path;

public class FeatureSplitResourceShrinkerDumpUtils {
  public static void setupBaseResourceShrinking(
      Path input, Path output, R8Command.Builder builder) {
    builder.setAndroidResourceProvider(new ArchiveProtoAndroidResourceProvider(input));
    builder.setAndroidResourceConsumer(new ArchiveProtoAndroidResourceConsumer(output));
  }
}
