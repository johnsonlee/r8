// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.isWindows;

import java.nio.file.Path;
import java.nio.file.Paths;

public enum ProguardVersion {
  V7_0_0("7.0.0"),
  V7_7_0("7.7.0");

  private final String version;

  ProguardVersion(String version) {
    this.version = version;
  }

  public static ProguardVersion getLatest() {
    return V7_7_0;
  }

  public Path getProguardScript() {
    return isWindows()
        ? getScriptDirectory().resolve("proguard.bat")
        : getScriptDirectory().resolve("proguard.sh");
  }

  public Path getRetraceScript() {
    return isWindows()
        ? getScriptDirectory().resolve("retrace.bat")
        : getScriptDirectory().resolve("retrace.sh");
  }

  private Path getScriptDirectory() {
    Path scriptDirectory = Paths.get(ToolHelper.THIRD_PARTY_DIR).resolve("proguard");
    if (version.startsWith("7.")) {
      scriptDirectory = scriptDirectory.resolve("proguard-" + version).resolve("bin");
    } else {
      scriptDirectory = scriptDirectory.resolve("proguard" + version).resolve("bin");
    }
    return scriptDirectory;
  }

  public String getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return "Proguard " + version;
  }
}
