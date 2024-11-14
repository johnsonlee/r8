// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import java.util.TreeSet;

class RegisterMoveCycle {

  private final TreeSet<RegisterMove> moves;

  // Whether the cycle is closed, i.e., the moves in the cycle are only blocked by moves also
  // present in this cycle.
  private final boolean closed;

  RegisterMoveCycle(TreeSet<RegisterMove> cycle, boolean closed) {
    this.moves = cycle;
    this.closed = closed;
  }

  public TreeSet<RegisterMove> getMoves() {
    return moves;
  }

  public boolean isClosed() {
    return closed;
  }
}
