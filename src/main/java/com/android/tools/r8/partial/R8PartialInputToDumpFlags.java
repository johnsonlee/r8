// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.utils.DumpInputFlags;
import java.nio.file.Path;

public class R8PartialInputToDumpFlags extends DumpInputFlags {

  private final Path dumpFile;

  public R8PartialInputToDumpFlags(Path dumpFile) {
    this.dumpFile = dumpFile;
  }

  @Override
  public Path getDumpPath() {
    return dumpFile;
  }

  @Override
  public boolean shouldDump(DumpOptions options) {
    return true;
  }

  @Override
  public boolean shouldFailCompilation() {
    return false;
  }

  @Override
  public boolean shouldLogDumpInfoMessage() {
    return false;
  }
}
