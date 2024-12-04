// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class AndroidOsBuildMethods {
  // Stub out android.os.Build$VERSION as it does not exist when building R8.
  private static class AndroidOsBuildStub {
    public static int getMajorSdkVersion(int sdkIntFull) {
      return -1;
    }

    public static int getMinorSdkVersion(int sdkIntFull) {
      return -1;
    }
  }

  // Stub out android.os.Build$VERSION as it does not exist when building R8.
  private static class AndroidOsBuildVersionStub {
    public static int SDK_INT;
  }

  public static int getMinorSdkVersion(int sdkIntFull) {
    if (AndroidOsBuildVersionStub.SDK_INT < 36) {
      // Based on the constants in android.os.Build$VERSION_CODES_FULL no minor SDK version prior
      // to SDK 36 where the method was also introduced.
      return 0;
    }
    return AndroidOsBuildStub.getMinorSdkVersion(sdkIntFull);
  }

  public static int getMajorSdkVersion(int sdkIntFull) {
    if (AndroidOsBuildVersionStub.SDK_INT < 36) {
      // Based on the constants in android.os.Build$VERSION_CODES_FULL.
      return sdkIntFull / 100_000;
    }
    return AndroidOsBuildStub.getMajorSdkVersion(sdkIntFull);
  }
}
