// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class KeepAttributeLibraryConsumerRuleDiagnostic implements Diagnostic {

  private final Origin origin;
  private final Position position;
  private final String attribute;

  public KeepAttributeLibraryConsumerRuleDiagnostic(
      Origin origin, Position position, String attribute) {
    this.origin = origin;
    this.position = position;
    this.attribute = attribute;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Illegal attempt to keep the attribute '" + attribute + "' in library consumer rules.";
  }
}
