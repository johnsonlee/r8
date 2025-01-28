// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.predicate;

import com.android.tools.r8.graph.DexString;

public class UnnamedPackageMatcher implements R8PartialPredicate {

  public static final String PATTERN = "L*";

  @Override
  public boolean test(DexString descriptor) {
    return descriptor.indexOf('/') == -1;
  }

  @Override
  public String serializeToString() {
    return "*";
  }
}
