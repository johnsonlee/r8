// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.DefaultEnqueuerUseRegistry;
import com.android.tools.r8.shaking.EnqueuerWorklist;

public interface NewlyLiveCodeEnqueuerAnalysis {

  /**
   * Called when a method's code has been processed by the registry. At this point the code has been
   * fully desugared and converted to LIR (if applicable).
   */
  default void processNewlyLiveCode(
      ProgramMethod method, DefaultEnqueuerUseRegistry registry, EnqueuerWorklist worklist) {}
}
