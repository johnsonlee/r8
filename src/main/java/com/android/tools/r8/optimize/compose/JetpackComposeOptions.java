// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SystemPropertyUtils;

public class JetpackComposeOptions {

  private final InternalOptions options;

  public boolean enableModelingOfChangedArguments =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.jetpackcompose.enableModelingOfChangedArguments", false);

  public JetpackComposeOptions(InternalOptions options) {
    this.options = options;
  }

  public boolean isModelingChangedArgumentsToComposableFunctions() {
    return options.isOptimizing() && options.isShrinking() && enableModelingOfChangedArguments;
  }
}
