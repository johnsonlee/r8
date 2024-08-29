// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import java.util.ArrayList;
import java.util.List;

public class InFlowPropagatorDebugUtils {

  // Return true for a given field here to enable extensive logging.
  @SuppressWarnings("UnusedVariable")
  private static boolean isLoggingEnabledFor(ProgramField field) {
    return false;
  }

  // Return true for a given method parameter here to enable extensive logging.
  @SuppressWarnings("UnusedVariable")
  private static boolean isLoggingEnabledFor(ProgramMethod method, int parameterIndex) {
    return false;
  }

  public static boolean setEnableLoggingBits(List<FlowGraph> flowGraphs) {
    for (FlowGraph flowGraph : flowGraphs) {
      setEnableLoggingBits(flowGraph);
    }
    return true;
  }

  private static void setEnableLoggingBits(FlowGraph flowGraph) {
    // Set the debug flag for the fields/parameters that we want to debug.
    flowGraph.forEachFieldNode(node -> node.setDebug(isLoggingEnabledFor(node.getField())));
    flowGraph.forEachParameterNode(
        node -> node.setDebug(isLoggingEnabledFor(node.getMethod(), node.getParameterIndex())));

    // Create a worklist containing these fields/parameters.
    WorkList<FlowGraphNode> worklist = WorkList.newIdentityWorkList();
    flowGraph.forEachNode(
        node -> {
          if (node.getDebug()) {
            worklist.addIfNotSeen(node);
          }
        });

    // Propagate the debug flag to all (transitive) predecessors to enable extensive logging for
    // these nodes as well.
    worklist.process(
        node -> {
          assert node.getDebug();
          for (FlowGraphNode predecessor : node.getPredecessors()) {
            if (predecessor.getDebug()) {
              assert worklist.isSeen(predecessor);
              continue;
            }
            predecessor.setDebug(true);
            worklist.addIfNotSeen(predecessor);
          }
        });
  }

  public static boolean logPropagateUnknown(FlowGraphNode node, FlowGraphNode successorNode) {
    if (successorNode.getDebug()) {
      log("PROPAGATE UNKNOWN", "FROM: " + node, "TO: " + successorNode);
    }
    return true;
  }

  public static boolean logPropagateConcrete(
      FlowGraphNode node,
      FlowGraphNode successorNode,
      ConcreteValueState nodeStateAfterNarrowing,
      AbstractFunction transferFunction,
      ValueState transferState,
      ValueState oldSuccessorState,
      FlowGraphStateProvider flowGraphStateProvider) {
    if (successorNode.getDebug()) {
      List<String> transferFunctionDependencies = new ArrayList<>();
      transferFunctionDependencies.add("");
      transferFunctionDependencies.add("TRANSFER FN INPUTS:");
      transferFunction.traverseBaseInFlow(
          transferFunctionDependency -> {
            if (!node.equalsBaseInFlow(transferFunctionDependency)) {
              ValueState transferFunctionDependencyState =
                  flowGraphStateProvider.getState(transferFunctionDependency, null);
              transferFunctionDependencies.add("  DEP: " + transferFunctionDependency);
              transferFunctionDependencies.add("  DEP STATE: " + transferFunctionDependencyState);
            }
            return TraversalContinuation.doContinue();
          });
      ValueState newSuccessorState = successorNode.getState();
      log(
          "PROPAGATE CONCRETE",
          "FROM: " + node,
          "TO: " + successorNode,
          "NODE STATE: " + node.getState(),
          "NODE STATE (NARROWED): "
              + (nodeStateAfterNarrowing.equals(node.getState())
                  ? "<unchanged>"
                  : nodeStateAfterNarrowing),
          "TRANSFER FN: " + transferFunction + StringUtils.joinLines(transferFunctionDependencies),
          "TRANSFER STATE: " + transferState,
          "SUCCESSOR STATE: " + oldSuccessorState,
          "SUCCESSOR STATE (NEW): "
              + (newSuccessorState.equals(oldSuccessorState) ? "<unchanged>" : newSuccessorState));
    }
    return true;
  }

  private static synchronized void log(String... lines) {
    System.out.println("====================================");
    for (String line : lines) {
      System.out.println(line);
    }
  }
}
