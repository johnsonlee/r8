// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.features.diagnostic;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@Keep
public class IllegalAccessWithIsolatedFeatureSplitsDiagnostic implements Diagnostic {

  private final DexReference accessedItem;
  private final ProgramMethod context;

  public IllegalAccessWithIsolatedFeatureSplitsDiagnostic(
      ProgramDefinition accessedItem, ProgramMethod context) {
    this.accessedItem = accessedItem.getReference();
    this.context = context;
  }

  @Override
  public Origin getOrigin() {
    return context.getOrigin();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    String kind = accessedItem.apply(clazz -> "class", field -> "field", method -> "method");
    return "Unexpected illegal access to non-public "
        + kind
        + " in another feature split (accessed: "
        + accessedItem.toSourceString()
        + ", context: "
        + context.toSourceString()
        + ").";
  }
}
