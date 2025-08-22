// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.SetUtils;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class IdentifierNameStringCollection {

  private final Set<DexMember<?, ?>> explicit;
  private final Set<DexMember<?, ?>> implicit;

  public IdentifierNameStringCollection(
      Set<DexMember<?, ?>> explicit, Set<DexMember<?, ?>> implicit) {
    this.explicit = explicit;
    this.implicit = implicit;
  }

  public boolean contains(DexMember<?, ?> member) {
    return explicit.contains(member) || implicit.contains(member);
  }

  public boolean containsExplicit(DexMember<?, ?> member) {
    return explicit.contains(member);
  }

  public IdentifierNameStringCollection prune(PrunedItems prunedItems, TaskCollection<?> tasks)
      throws ExecutionException {
    if (prunedItems.hasRemovedMembers()) {
      tasks.submit(
          () -> {
            prunedItems.getRemovedFields().forEach(this::prune);
            prunedItems.getRemovedMethods().forEach(this::prune);
          });
    }
    return this;
  }

  private void prune(DexMember<?, ?> member) {
    explicit.remove(member);
    implicit.remove(member);
  }

  public IdentifierNameStringCollection rewrittenWithLens(GraphLens lens) {
    return new IdentifierNameStringCollection(
        rewrittenWithLens(lens, explicit), rewrittenWithLens(lens, implicit));
  }

  private static Set<DexMember<?, ?>> rewrittenWithLens(
      GraphLens lens, Set<DexMember<?, ?>> members) {
    return SetUtils.mapIdentityHashSet(members, lens::rewriteReference);
  }
}
