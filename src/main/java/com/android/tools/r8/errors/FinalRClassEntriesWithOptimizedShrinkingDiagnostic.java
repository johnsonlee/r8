// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class FinalRClassEntriesWithOptimizedShrinkingDiagnostic implements Diagnostic {

  private final Origin origin;
  private final DexField dexField;

  public FinalRClassEntriesWithOptimizedShrinkingDiagnostic(Origin origin, DexField dexField) {
    this.origin = origin;
    this.dexField = dexField;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Running optimized resource shrinking with final R class ids is not supported and "
        + "can lead to missing resources and code necessary for program execution. "
        + "Field "
        + dexField
        + " is final";
  }
}
