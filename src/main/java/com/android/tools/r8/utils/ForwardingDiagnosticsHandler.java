// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DiagnosticsLevel;

public class ForwardingDiagnosticsHandler implements DiagnosticsHandler {

  private final DiagnosticsHandler diagnosticsHandler;

  public ForwardingDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler) {
    this.diagnosticsHandler = diagnosticsHandler;
  }

  @Override
  public void error(Diagnostic error) {
    diagnosticsHandler.error(error);
  }

  @Override
  public void warning(Diagnostic warning) {
    diagnosticsHandler.warning(warning);
  }

  @Override
  public void info(Diagnostic info) {
    diagnosticsHandler.info(info);
  }

  @Override
  public DiagnosticsLevel modifyDiagnosticsLevel(DiagnosticsLevel level, Diagnostic diagnostic) {
    return diagnosticsHandler.modifyDiagnosticsLevel(level, diagnostic);
  }
}
