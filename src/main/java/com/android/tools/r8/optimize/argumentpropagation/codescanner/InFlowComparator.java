// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InFlowComparator implements Comparator<InFlow> {

  private final Map<ComputationTreeNode, SourcePosition> computationTreePositions;
  private final Map<IfThenElseAbstractFunction, SourcePosition> ifThenElsePositions;

  private InFlowComparator(
      Map<ComputationTreeNode, SourcePosition> computationTreePositions,
      Map<IfThenElseAbstractFunction, SourcePosition> ifThenElsePositions) {
    this.computationTreePositions = computationTreePositions;
    this.ifThenElsePositions = ifThenElsePositions;
  }

  public SourcePosition getComputationTreePosition(ComputationTreeNode computation) {
    SourcePosition position = computationTreePositions.get(computation);
    assert position != null
        : "Unexpected attempt to lookup position of " + computation.getClass().getName();
    return position;
  }

  public SourcePosition getIfThenElsePosition(IfThenElseAbstractFunction fn) {
    SourcePosition position = ifThenElsePositions.get(fn);
    assert position != null;
    return position;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void clear() {
    ifThenElsePositions.clear();
  }

  @Override
  public int compare(InFlow inFlow, InFlow other) {
    return inFlow.compareTo(other, this);
  }

  public static class Builder {

    private final Map<ComputationTreeNode, SourcePosition> computationTreePositions =
        new ConcurrentHashMap<>();
    private final Map<IfThenElseAbstractFunction, SourcePosition> ifThenElsePositions =
        new ConcurrentHashMap<>();

    public void addComputationTreePosition(
        ComputationTreeNode computation, SourcePosition position) {
      computationTreePositions.put(computation, position);
    }

    public void addIfThenElsePosition(IfThenElseAbstractFunction fn, SourcePosition position) {
      ifThenElsePositions.put(fn, position);
    }

    public InFlowComparator build() {
      return new InFlowComparator(
          new HashMap<>(computationTreePositions), new HashMap<>(ifThenElsePositions));
    }
  }
}
