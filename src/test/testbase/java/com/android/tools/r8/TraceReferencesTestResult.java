// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.ThrowingConsumer;

public class TraceReferencesTestResult {

  private final TraceReferencesInspector inspector;

  TraceReferencesTestResult(TraceReferencesInspector inspector) {
    this.inspector = inspector;
  }

  public <E extends Exception> TraceReferencesTestResult inspect(
      ThrowingConsumer<TraceReferencesInspector, E> fn) throws E {
    fn.accept(inspector);
    return this;
  }
}
