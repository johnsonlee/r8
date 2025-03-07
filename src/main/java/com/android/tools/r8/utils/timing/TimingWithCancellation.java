// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import com.android.tools.r8.utils.CancelCompilationException;
import com.android.tools.r8.utils.InternalOptions;

public class TimingWithCancellation extends TimingDelegate {

  private final InternalOptions options;

  public TimingWithCancellation(InternalOptions options, Timing timing) {
    super(timing);
    this.options = options;
  }

  @Override
  public Timing begin(String title) {
    if (options.checkIfCancelled()) {
      throw new CancelCompilationException();
    }
    return super.begin(title);
  }
}
