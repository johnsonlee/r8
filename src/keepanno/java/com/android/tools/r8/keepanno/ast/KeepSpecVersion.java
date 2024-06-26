// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.Version;

public enum KeepSpecVersion {
  UNKNOWN(0, 0, 0),
  ALPHA(0, 1, 0);

  private final int major;
  private final int minor;
  private final int patch;

  KeepSpecVersion(int major, int minor, int patch) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
  }

  public static KeepSpecVersion getCurrent() {
    return ALPHA;
  }

  public static KeepSpecVersion fromProto(Version version) {
    for (KeepSpecVersion value : values()) {
      if (value.major == version.getMajor()
          && value.minor == version.getMinor()
          && value.patch == version.getPatch()) {
        return value;
      }
    }
    return UNKNOWN;
  }

  public String toVersionString() {
    return "" + major + "." + minor + "." + patch;
  }

  public Version.Builder buildProto() {
    return Version.newBuilder().setMajor(major).setMinor(minor).setPatch(patch);
  }
}
