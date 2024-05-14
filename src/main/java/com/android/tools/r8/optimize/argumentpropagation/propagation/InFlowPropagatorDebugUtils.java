// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.WorkList;
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
}
