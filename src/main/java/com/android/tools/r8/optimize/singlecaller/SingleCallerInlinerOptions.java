// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.singlecaller;

import com.android.tools.r8.utils.InternalOptions;

public class SingleCallerInlinerOptions {

  private final InternalOptions options;

  private boolean enable = true;

  public SingleCallerInlinerOptions(InternalOptions options) {
    this.options = options;
  }

  public boolean isEnabled() {
    return enable
        && !options.debug
        && !options.intermediate
        && options.isOptimizing()
        && options.isShrinking();
  }

  public void setEnable(boolean enable) {
    this.enable = enable;
  }
}
