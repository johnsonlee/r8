// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.compiledump;

import com.android.tools.r8.D8Command;
import com.android.tools.r8.R8Command;

// Simple boolean commands directly on compiler command builders
public class CompilerCommandDumpUtils {
  public static void setEnableExperimentalMissingLibraryApiModeling(
      R8Command.Builder builder, boolean enable) {
    builder.setEnableExperimentalMissingLibraryApiModeling(enable);
  }

  public static void setEnableExperimentalMissingLibraryApiModeling(
      D8Command.Builder builder, boolean enable) {
    builder.setEnableExperimentalMissingLibraryApiModeling(enable);
  }

  public static void setAndroidPlatformBuild(
      R8Command.Builder builder, boolean androidPlatformBuild) {
    builder.setAndroidPlatformBuild(androidPlatformBuild);
  }

  public static void setAndroidPlatformBuild(
      D8Command.Builder builder, boolean androidPlatformBuild) {
    builder.setAndroidPlatformBuild(androidPlatformBuild);
  }

  public static void setIsolatedSplits(R8Command.Builder builder, boolean isolatedSplits) {
    builder.setEnableIsolatedSplits(isolatedSplits);
  }
}
