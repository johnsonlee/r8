// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.lens;

import java.util.ArrayDeque;
import java.util.Deque;

public class GraphLensUtils {

  public static Deque<NonIdentityGraphLens> extractNonIdentityLenses(GraphLens lens) {
    Deque<NonIdentityGraphLens> lenses = new ArrayDeque<>();
    if (lens.isNonIdentityLens()) {
      lenses.addFirst(lens.asNonIdentityLens());
      while (true) {
        GraphLens previous = lenses.getFirst().getPrevious();
        if (previous.isNonIdentityLens()) {
          lenses.addFirst(previous.asNonIdentityLens());
        } else {
          break;
        }
      }
    }
    return lenses;
  }
}
