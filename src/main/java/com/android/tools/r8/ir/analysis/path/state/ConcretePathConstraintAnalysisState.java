// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path.state;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Represents a non-trivial (neither bottom nor top) path constraint that must be satisfied to reach
 * a given program point.
 *
 * <p>The state should be interpreted as follows:
 *
 * <ol>
 *   <li>If a path constraint is DISABLED then the path constraint should be IGNORED.
 *   <li>If a path constraint is POSITIVE then the current program point can only be reached if the
 *       path constraint is satisfied.
 *   <li>If a path constraint is NEGATIVE then the current program point can only be reached if the
 *       path constraint is NOT satisfied.
 * </ol>
 *
 * <p>Example: In the below example, when entering the IF-THEN branch, we add [(flags & 1) != 0] as
 * a POSITIVE path constraint to {@link #pathConstraints}. When entering the (empty) IF-ELSE branch,
 * we add the same expression as a NEGATIVE path constraint to {@link #pathConstraints}. When
 * reaching the return block, the two path constraint states are joined, resulting in the state
 * where the constraint [(flags & 1) != 0] is DISABLED.
 *
 * <pre>
 *   static Object Foo(Object o, int flags) {
 *     if ((flags & 1) != 0) {
 *       o = DEFAULT_VALUE;
 *     }
 *     return o;
 *   }
 * </pre>
 */
public class ConcretePathConstraintAnalysisState extends PathConstraintAnalysisState {

  // TODO(b/302281503): Consider changing this to an ImmutableMap.
  private final Map<ComputationTreeNode, PathConstraintKind> pathConstraints;

  public ConcretePathConstraintAnalysisState() {
    this.pathConstraints = Collections.emptyMap();
  }

  ConcretePathConstraintAnalysisState(
      Map<ComputationTreeNode, PathConstraintKind> pathConstraints) {
    this.pathConstraints = pathConstraints;
  }

  static ConcretePathConstraintAnalysisState create(
      ComputationTreeNode pathConstraint, boolean negate) {
    return new ConcretePathConstraintAnalysisState(
        Collections.singletonMap(pathConstraint, PathConstraintKind.get(negate)));
  }

  @Override
  public PathConstraintAnalysisState add(ComputationTreeNode pathConstraint, boolean negate) {
    PathConstraintKind previousKind = pathConstraints.get(pathConstraint);
    if (previousKind != null) {
      if (previousKind == PathConstraintKind.DISABLED) {
        // There is a loop.
        return this;
      }
      if (previousKind == PathConstraintKind.get(negate)) {
        // This branch is dominated by a previous if-condition that has the same branch condition,
        // e.g., if (x) { if (x) { ...
        return this;
      }
      // This branch is dominated by a previous if-condition that has the negated branch condition,
      // e.g., if (x) { if (!x) { ...
      return bottom();
    }
    // No jumps can dominate the entry of their own block, so when adding the condition of a jump
    // this cannot currently be active.
    Map<ComputationTreeNode, PathConstraintKind> newPathConstraints =
        new HashMap<>(pathConstraints.size() + 1);
    newPathConstraints.putAll(pathConstraints);
    newPathConstraints.put(pathConstraint, PathConstraintKind.get(negate));
    return new ConcretePathConstraintAnalysisState(newPathConstraints);
  }

  public Map<ComputationTreeNode, PathConstraintKind> getPathConstraintsForTesting() {
    return pathConstraints;
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcretePathConstraintAnalysisState asConcreteState() {
    return this;
  }

  @Override
  public boolean isGreaterThanOrEquals(AppView<?> appView, PathConstraintAnalysisState state) {
    if (state.isConcrete()) {
      return isGreaterThanOrEquals(state.asConcreteState());
    }
    return super.isGreaterThanOrEquals(appView, state);
  }

  private boolean isGreaterThanOrEquals(ConcretePathConstraintAnalysisState other) {
    // The current state must contain all keys of the other state.
    if (pathConstraints.size() < other.pathConstraints.size()) {
      return false;
    }
    for (ComputationTreeNode otherPathConstraint : other.pathConstraints.keySet()) {
      if (!pathConstraints.containsKey(otherPathConstraint)) {
        return false;
      }
    }
    // The current path constraint kinds must be the join of the kinds.
    for (Entry<ComputationTreeNode, PathConstraintKind> entry : pathConstraints.entrySet()) {
      ComputationTreeNode pathConstraint = entry.getKey();
      PathConstraintKind kind = entry.getValue();
      PathConstraintKind otherKind = other.pathConstraints.get(pathConstraint);
      PathConstraintKind joinKind = kind.join(otherKind);
      if (kind != joinKind) {
        return false;
      }
    }
    return true;
  }

  public boolean isNegated(ComputationTreeNode pathConstraint) {
    PathConstraintKind kind = pathConstraints.get(pathConstraint);
    assert kind != null;
    assert kind != PathConstraintKind.DISABLED;
    return kind == PathConstraintKind.NEGATIVE;
  }

  // TODO(b/302281503): Consider returning the list of differentiating path constraints.
  //  For example, if we have the condition X & Y, then we may not know anything about X but we
  //  could know something about Y. Add a test showing this.
  public ComputationTreeNode getDifferentiatingPathConstraint(
      ConcretePathConstraintAnalysisState other) {
    for (Entry<ComputationTreeNode, PathConstraintKind> entry : pathConstraints.entrySet()) {
      ComputationTreeNode pathConstraint = entry.getKey();
      PathConstraintKind kind = entry.getValue();
      if (kind == PathConstraintKind.DISABLED) {
        continue;
      }
      PathConstraintKind otherKind = other.pathConstraints.get(pathConstraint);
      if (otherKind != null && kind.isNegation(otherKind)) {
        return pathConstraint;
      }
    }
    return null;
  }

  public ConcretePathConstraintAnalysisState join(ConcretePathConstraintAnalysisState other) {
    if (isGreaterThanOrEquals(other)) {
      return this;
    }
    if (other.isGreaterThanOrEquals(this)) {
      return other;
    }
    Map<ComputationTreeNode, PathConstraintKind> newPathConstraints =
        new HashMap<>(pathConstraints.size() + other.pathConstraints.size());
    join(other, newPathConstraints);
    other.join(this, newPathConstraints);
    return new ConcretePathConstraintAnalysisState(newPathConstraints);
  }

  private void join(
      ConcretePathConstraintAnalysisState other,
      Map<ComputationTreeNode, PathConstraintKind> newPathConstraints) {
    for (Entry<ComputationTreeNode, PathConstraintKind> entry : pathConstraints.entrySet()) {
      ComputationTreeNode pathConstraint = entry.getKey();
      PathConstraintKind kind = entry.getValue();
      PathConstraintKind otherKind = other.pathConstraints.get(pathConstraint);
      PathConstraintKind joinKind = kind.join(otherKind);
      newPathConstraints.put(pathConstraint, joinKind);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ConcretePathConstraintAnalysisState)) {
      return false;
    }
    ConcretePathConstraintAnalysisState state = (ConcretePathConstraintAnalysisState) obj;
    return pathConstraints.equals(state.pathConstraints);
  }

  @Override
  public int hashCode() {
    return pathConstraints.hashCode();
  }
}
