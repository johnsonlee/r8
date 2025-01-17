// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import java.nio.file.Path;

public class R8PartialDesugarResult {

  private final Path outputPath;

  public R8PartialDesugarResult(Path outputPath) {
    this.outputPath = outputPath;
  }

  public Path getOutputPath() {
    return outputPath;
  }
}
