// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.singlecaller;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.CallGraphBase;
import com.android.tools.r8.ir.conversion.callgraph.CallGraphBuilderBase;
import com.android.tools.r8.ir.conversion.callgraph.CycleEliminator;
import com.android.tools.r8.ir.conversion.callgraph.CycleEliminatorNode;
import com.android.tools.r8.ir.conversion.callgraph.NodeBase;
import com.android.tools.r8.optimize.singlecaller.SingleCallerInlinerCallGraph.Node;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SingleCallerInlinerCallGraph extends CallGraphBase<Node> {

  public SingleCallerInlinerCallGraph(Map<DexMethod, Node> nodes) {
    super(nodes);
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  public ProgramMethodSet extractLeaves() {
    ProgramMethodSet result = ProgramMethodSet.create();
    Set<Node> removed = Sets.newIdentityHashSet();
    Iterator<Node> nodeIterator = getNodes().iterator();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.next();
      if (node.isLeaf()) {
        result.add(node.getProgramMethod());
        nodeIterator.remove();
        removed.add(node);
      }
    }
    removed.forEach(Node::cleanForRemoval);
    return result;
  }

  public static class Builder extends CallGraphBuilderBase<Node> {

    public Builder(AppView<AppInfoWithLiveness> appView) {
      super(appView);
    }

    public Builder populateGraph(ProgramMethodMap<ProgramMethod> singleCallerMethods) {
      singleCallerMethods.forEach(
          (callee, caller) -> getOrCreateNode(callee).addCaller(getOrCreateNode(caller)));
      return this;
    }

    public Builder eliminateCycles() {
      // Sort the nodes for deterministic cycle elimination.
      Set<Node> nodesWithDeterministicOrder = Sets.newTreeSet(nodes.values());
      new CycleEliminator<Node>().breakCycles(nodesWithDeterministicOrder);
      return this;
    }

    public SingleCallerInlinerCallGraph build() {
      return new SingleCallerInlinerCallGraph(nodes);
    }

    @Override
    protected Node createNode(ProgramMethod method) {
      return new Node(method);
    }
  }

  public static class Node extends NodeBase<Node>
      implements Comparable<Node>, CycleEliminatorNode<Node> {

    private Node caller = null;
    private final Set<Node> callees = new TreeSet<>();

    public Node(ProgramMethod method) {
      super(method);
    }

    public void addCaller(Node caller) {
      assert this.caller == null;
      if (this == caller) {
        return;
      }
      this.caller = caller;
      caller.callees.add(this);
    }

    public void cleanForRemoval() {
      assert callees.isEmpty();
      if (caller != null) {
        caller.callees.remove(this);
        caller = null;
      }
    }

    public boolean isLeaf() {
      return callees.isEmpty();
    }

    @Override
    public void addCallerConcurrently(Node caller, boolean likelySpuriousCallEdge) {
      throw new Unreachable();
    }

    @Override
    public void addReaderConcurrently(Node reader) {
      throw new Unreachable();
    }

    @Override
    public int compareTo(Node node) {
      return getProgramMethod().getReference().compareTo(node.getProgramMethod().getReference());
    }

    @Override
    public Set<Node> getCalleesWithDeterministicOrder() {
      return callees;
    }

    @Override
    public Set<Node> getWritersWithDeterministicOrder() {
      return Collections.emptySet();
    }

    @Override
    public boolean hasCallee(Node callee) {
      return callees.contains(callee);
    }

    @Override
    public boolean hasCaller(Node caller) {
      return this.caller != null && this.caller == caller;
    }

    @Override
    public void removeCaller(Node caller) {
      assert this.caller != null;
      assert this.caller == caller;
      boolean changed = caller.callees.remove(this);
      assert changed;
      this.caller = null;
    }

    @Override
    public boolean hasReader(Node reader) {
      return false;
    }

    @Override
    public void removeReader(Node reader) {
      throw new Unreachable();
    }

    @Override
    public boolean hasWriter(Node writer) {
      return false;
    }
  }
}
