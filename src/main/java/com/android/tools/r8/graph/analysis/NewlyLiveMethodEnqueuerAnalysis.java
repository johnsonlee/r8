// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;

public interface NewlyLiveMethodEnqueuerAnalysis {

  /** Called when a method is found to be live. */
  default void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {}
}
