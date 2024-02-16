// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Set;

public interface CycleEliminatorNode<N extends CycleEliminatorNode<N>> {

  DexEncodedMethod getMethod();

  ProgramMethod getProgramMethod();

  Set<N> getCalleesWithDeterministicOrder();

  Set<N> getWritersWithDeterministicOrder();

  boolean hasCallee(N callee);

  boolean hasCaller(N caller);

  void removeCaller(N caller);

  boolean hasReader(N reader);

  void removeReader(N reader);

  boolean hasWriter(N writer);
}
