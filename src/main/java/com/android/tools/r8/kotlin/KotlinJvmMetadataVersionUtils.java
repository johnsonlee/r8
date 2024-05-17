// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlin.metadata.jvm.JvmMetadataVersion;

public class KotlinJvmMetadataVersionUtils {

  public static JvmMetadataVersion MIN_SUPPORTED_VERSION = new JvmMetadataVersion(1, 4, 0);

  public static int[] toIntArray(JvmMetadataVersion version) {
    return new int[] {version.getMajor(), version.getMinor(), version.getPatch()};
  }
}
