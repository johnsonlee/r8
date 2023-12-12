// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.lens;

import java.util.ArrayDeque;
import java.util.Deque;

public class GraphLensUtils {

  public static Deque<NonIdentityGraphLens> extractNonIdentityLenses(GraphLens lens) {
    Deque<NonIdentityGraphLens> lenses = new ArrayDeque<>();
    while (lens.isNonIdentityLens()) {
      NonIdentityGraphLens nonIdentityLens = lens.asNonIdentityLens();
      lenses.addFirst(nonIdentityLens);
      lens = nonIdentityLens.getPrevious();
    }
    return lenses;
  }
}
