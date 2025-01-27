// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.AndroidResourceConsumer;
import com.android.tools.r8.ProgramConsumer;

public class FeatureSplitConsumers {

  private final ProgramConsumer programConsumer;
  private final AndroidResourceConsumer androidResourceConsumer;

  public FeatureSplitConsumers(
      ProgramConsumer programConsumer, AndroidResourceConsumer androidResourceConsumer) {
    this.programConsumer = programConsumer;
    this.androidResourceConsumer = androidResourceConsumer;
  }

  public ProgramConsumer getProgramConsumer() {
    return programConsumer;
  }

  public AndroidResourceConsumer getAndroidResourceConsumer() {
    return androidResourceConsumer;
  }
}
