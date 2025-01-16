// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import it.unimi.dsi.fastutil.ints.IntSet;

public class R8PartialTraceResourcesResult {

  private final IntSet resourceIdsToTrace;

  public R8PartialTraceResourcesResult(IntSet resourceIdsToTrace) {
    this.resourceIdsToTrace = resourceIdsToTrace;
  }

  public IntSet getResourceIdsToTrace() {
    return resourceIdsToTrace;
  }
}
