// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.EnqueuerWorklist;

public interface TraceFieldAccessEnqueuerAnalysis {

  default void traceInstanceFieldRead(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {}

  default void traceInstanceFieldWrite(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {}

  default void traceStaticFieldRead(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {}

  default void traceStaticFieldWrite(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {}
}
