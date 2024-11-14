// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.conversion.MethodProcessorWithWave;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation.CallGraphBasedCallSiteInformation;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Call graph representation.
 *
 * <p>Each node in the graph contain the methods called and the calling methods. For virtual and
 * interface calls all potential calls from subtypes are recorded.
 *
 * <p>Only methods in the program - not library methods - are
 * represented. @SuppressWarnings("UnusedVariable")
 *
 * <p>The directional edges are represented as sets of nodes in each node (called methods and
 * callees).
 *
 * <p>A call from method <code>a</code> to method <code>b</code> is only present once no matter how
 * many calls of <code>a</code> there are in <code>a</code>.
 *
 * <p>Recursive calls are not present.
 */
public class CallGraph extends CallGraphBase<Node> {

  CallGraph(Map<DexMethod, Node> nodes) {
    super(nodes);
  }

  public static CallGraphBuilder builder(AppView<AppInfoWithLiveness> appView) {
    return new CallGraphBuilder(appView);
  }

  public static CallGraph createForTesting(Collection<Node> nodes) {
    return new CallGraph(
        nodes.stream()
            .collect(
                Collectors.toMap(
                    node -> node.getProgramMethod().getReference(), Function.identity())));
  }

  public CallSiteInformation createCallSiteInformation(
      AppView<AppInfoWithLiveness> appView, MethodProcessorWithWave methodProcessor) {
    return needsCallSiteInformation(appView)
        ? new CallGraphBasedCallSiteInformation(appView, this, methodProcessor)
        : CallSiteInformation.empty();
  }

  private boolean needsCallSiteInformation(AppView<AppInfoWithLiveness> appView) {
    InternalOptions options = appView.options();
    if (options.debug) {
      return false;
    }
    if (options.isOptimizing() && options.isShrinking()) {
      return true;
    }
    // When we are neither optimizing nor shrinking, we still allow inlining of javac synthetic
    // lambda methods into R8 generated accessor methods.
    return options.isGeneratingDex();
  }

  public ProgramMethodSet extractLeaves() {
    return extractNodes(Node::isLeaf, Node::cleanCallersAndReadersForRemoval);
  }

  public ProgramMethodSet extractLeaves(Consumer<Node> nodeRemovalConsumer) {
    return extractNodes(
        Node::isLeaf,
        node -> {
          nodeRemovalConsumer.accept(node);
          node.cleanCallersAndReadersForRemoval();
        });
  }

  public ProgramMethodSet extractRoots() {
    return extractNodes(Node::isRoot, Node::cleanCalleesAndWritersForRemoval);
  }

  private ProgramMethodSet extractNodes(Predicate<Node> predicate, Consumer<Node> clean) {
    ProgramMethodSet result =
        InternalOptions.DETERMINISTIC_DEBUGGING
            ? SortedProgramMethodSet.create()
            : ProgramMethodSet.create();
    Set<Node> removed = Sets.newIdentityHashSet();
    Iterator<Node> nodeIterator = nodes.values().iterator();
    while (nodeIterator.hasNext()) {
      Node node = nodeIterator.next();
      if (predicate.test(node)) {
        result.add(node.getProgramMethod());
        nodeIterator.remove();
        removed.add(node);
      }
    }
    removed.forEach(clean);
    assert !result.isEmpty();
    return result;
  }
}
