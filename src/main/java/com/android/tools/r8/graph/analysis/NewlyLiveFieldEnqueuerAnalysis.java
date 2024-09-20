// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.shaking.EnqueuerWorklist;

public interface NewlyLiveFieldEnqueuerAnalysis {

  /** Called when a field is found to be live. */
  default void processNewlyLiveField(
      ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {}
}
