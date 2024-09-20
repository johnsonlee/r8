// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.shaking.Enqueuer;

public interface FinishedEnqueuerAnalysis {

  /**
   * Called when the Enqueuer has reached the final fixpoint. Each analysis may use this callback to
   * perform some post-processing.
   */
  default void done(Enqueuer enqueuer) {}
}
