// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.path.state;

import com.android.tools.r8.optimize.argumentpropagation.computation.ComputationTreeNode;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a non-trivial (neither bottom nor top) path constraint that must be satisfied to reach
 * a given program point.
 *
 * <p>The state should be interpreted as follows:
 *
 * <ol>
 *   <li>If a path constraint is BOTH in {@link #pathConstraints} AND {@link
 *       #negatedPathConstraints} then the path constraint should be IGNORED.
 *   <li>If a path constraint is ONLY in {@link #pathConstraints} then the current program point can
 *       only be reached if the path constraint is satisfied.
 *   <li>If a path constraint is ONLY in {@link #negatedPathConstraints} then the current program
 *       point can only be reached if the path constraint is NOT satisfied.
 * </ol>
 *
 * <p>Example: In the below example, when entering the IF-THEN branch, we add the path constraint
 * [(flags & 1) != 0] to {@link #pathConstraints}. When entering the (empty) IF-ELSE branch, we add
 * the same path constraint to {@link #negatedPathConstraints}. When reaching the return block, the
 * two path constraint states are joined, resulting in the state where {@link #pathConstraints} and
 * {@link #negatedPathConstraints} both contains the constraint [(flags & 1) != 0].
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

  private final Set<ComputationTreeNode> pathConstraints;
  private final Set<ComputationTreeNode> negatedPathConstraints;

  ConcretePathConstraintAnalysisState(
      Set<ComputationTreeNode> pathConstraints, Set<ComputationTreeNode> negatedPathConstraints) {
    this.pathConstraints = pathConstraints;
    this.negatedPathConstraints = negatedPathConstraints;
  }

  static ConcretePathConstraintAnalysisState create(
      ComputationTreeNode pathConstraint, boolean negate) {
    Set<ComputationTreeNode> pathConstraints = Collections.singleton(pathConstraint);
    if (negate) {
      return new ConcretePathConstraintAnalysisState(Collections.emptySet(), pathConstraints);
    } else {
      return new ConcretePathConstraintAnalysisState(pathConstraints, Collections.emptySet());
    }
  }

  @Override
  public PathConstraintAnalysisState add(ComputationTreeNode pathConstraint, boolean negate) {
    if (negate) {
      if (negatedPathConstraints.contains(pathConstraint)) {
        return this;
      }
      return new ConcretePathConstraintAnalysisState(
          pathConstraints, add(pathConstraint, negatedPathConstraints));
    } else {
      if (pathConstraints.contains(pathConstraint)) {
        return this;
      }
      return new ConcretePathConstraintAnalysisState(
          add(pathConstraint, pathConstraints), negatedPathConstraints);
    }
  }

  private static Set<ComputationTreeNode> add(
      ComputationTreeNode pathConstraint, Set<ComputationTreeNode> pathConstraints) {
    if (pathConstraints.isEmpty()) {
      return Collections.singleton(pathConstraint);
    }
    assert !pathConstraints.contains(pathConstraint);
    Set<ComputationTreeNode> newPathConstraints = new HashSet<>(pathConstraints);
    newPathConstraints.add(pathConstraint);
    return newPathConstraints;
  }

  public Set<ComputationTreeNode> getPathConstraints() {
    return pathConstraints;
  }

  public Set<ComputationTreeNode> getNegatedPathConstraints() {
    return negatedPathConstraints;
  }

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcretePathConstraintAnalysisState asConcreteState() {
    return this;
  }

  public ConcretePathConstraintAnalysisState join(ConcretePathConstraintAnalysisState other) {
    Set<ComputationTreeNode> newPathConstraints = join(pathConstraints, other.pathConstraints);
    Set<ComputationTreeNode> newNegatedPathConstraints =
        join(negatedPathConstraints, other.negatedPathConstraints);
    if (identical(newPathConstraints, newNegatedPathConstraints)) {
      return this;
    }
    if (other.identical(newPathConstraints, newNegatedPathConstraints)) {
      return other;
    }
    return new ConcretePathConstraintAnalysisState(newPathConstraints, newNegatedPathConstraints);
  }

  private static Set<ComputationTreeNode> join(
      Set<ComputationTreeNode> pathConstraints, Set<ComputationTreeNode> otherPathConstraints) {
    if (pathConstraints.isEmpty()) {
      return otherPathConstraints;
    }
    if (otherPathConstraints.isEmpty()) {
      return pathConstraints;
    }
    Set<ComputationTreeNode> newPathConstraints =
        new HashSet<>(pathConstraints.size() + otherPathConstraints.size());
    newPathConstraints.addAll(pathConstraints);
    newPathConstraints.addAll(otherPathConstraints);
    return newPathConstraints;
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
    return pathConstraints.equals(state.pathConstraints)
        && negatedPathConstraints.equals(state.negatedPathConstraints);
  }

  public boolean identical(
      Set<ComputationTreeNode> pathConstraints, Set<ComputationTreeNode> negatedPathConstraints) {
    return this.pathConstraints == pathConstraints
        && this.negatedPathConstraints == negatedPathConstraints;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pathConstraints, negatedPathConstraints);
  }
}
