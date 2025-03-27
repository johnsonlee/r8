// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.shaking.KeepReason;

public interface MarkFieldAsKeptEnqueuerAnalysis {

  /** Called when a field is marked as kept. */
  void processNewlyKeptField(ProgramField field, KeepReason keepReason, EnqueuerWorklist worklist);
}
