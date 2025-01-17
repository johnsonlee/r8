// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

public abstract class R8PartialSubCompilationConfiguration {

  public final Timing timing;

  R8PartialSubCompilationConfiguration(Timing timing) {
    this.timing = timing;
  }

  public IntSet getD8TracedResourceIds() {
    return IntSets.EMPTY_SET;
  }

  public boolean includeMarker() {
    return false;
  }

  // Currently shared by all D8 compilations in R8 partial (i.e., dexing, desugaring, merging).
  public static class R8PartialD8SubCompilationConfiguration
      extends R8PartialSubCompilationConfiguration {

    public R8PartialD8SubCompilationConfiguration(Timing timing) {
      super(timing);
    }
  }

  public static class R8PartialR8SubCompilationConfiguration
      extends R8PartialSubCompilationConfiguration {

    private final IntSet d8TracedResourceIds;

    public R8PartialR8SubCompilationConfiguration(IntSet d8TracedResourceIds, Timing timing) {
      super(timing);
      this.d8TracedResourceIds = d8TracedResourceIds;
    }

    @Override
    public IntSet getD8TracedResourceIds() {
      return d8TracedResourceIds;
    }

    @Override
    public boolean includeMarker() {
      return true;
    }
  }
}
