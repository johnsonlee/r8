// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class GlobalLibraryConsumerRuleDiagnostic implements Diagnostic {
  private final Origin origin;
  private final Position position;
  private final String rule;

  public GlobalLibraryConsumerRuleDiagnostic(Origin origin, Position position, String rule) {
    this.origin = origin;
    this.position = position;
    this.rule = rule;
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
    return rule + " not allowed in library consumer rules.";
  }
}
