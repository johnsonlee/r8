// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.classmerging.ClassMergerMode;
import com.android.tools.r8.utils.InternalOptions;

public class VerticalClassMergerOptions {

  private final InternalOptions options;

  private boolean enabled = true;
  private boolean enableInitial = true;
  private boolean enableBridgeAnalysis = true;

  public VerticalClassMergerOptions(InternalOptions options) {
    this.options = options;
  }

  public void disable() {
    setEnabled(false);
  }

  public void disableInitial() {
    enableInitial = false;
  }

  public boolean isEnabled(ClassMergerMode mode) {
    if (!enabled
        || options.debug
        || options.intermediate
        || !options.isOptimizing()
        || !options.isShrinking()) {
      return false;
    }
    if (mode.isInitial() && !enableInitial) {
      return false;
    }
    return true;
  }

  public boolean isBridgeAnalysisEnabled() {
    return enableBridgeAnalysis;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setEnableBridgeAnalysis(boolean enableBridgeAnalysis) {
    this.enableBridgeAnalysis = enableBridgeAnalysis;
  }
}
