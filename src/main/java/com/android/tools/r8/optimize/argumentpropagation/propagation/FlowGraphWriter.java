// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InFlowPropagator.FieldNode;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InFlowPropagator.FlowGraph;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InFlowPropagator.Node;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InFlowPropagator.ParameterNode;
import com.google.common.annotations.VisibleForTesting;
import java.io.PrintStream;

// A simple (unused) writer to aid debugging of field and parameter flow propagation graphs.
@VisibleForTesting
public class FlowGraphWriter {

  private final FlowGraph flowGraph;

  public FlowGraphWriter(FlowGraph flowGraph) {
    this.flowGraph = flowGraph;
  }

  public void write(PrintStream out) {
    out.println("digraph {");
    out.println("  stylesheet = \"/frameworks/g3doc/includes/graphviz-style.css\"");
    flowGraph.forEachNode(node -> writeNode(out, node));
    out.println("}");
  }

  private void writeEdge(PrintStream out) {
    out.println(" -> ");
  }

  private void writeNode(PrintStream out, Node node) {
    if (!node.hasSuccessors()) {
      writeNodeLabel(out, node);
      return;
    }
    node.forEachSuccessor(
        (successor, transferFunctions) -> {
          writeNodeLabel(out, node);
          writeEdge(out);
          writeNodeLabel(out, successor);
        });
  }

  private void writeNodeLabel(PrintStream out, Node node) {
    if (node.isFieldNode()) {
      writeFieldNodeLabel(out, node.asFieldNode());
    } else {
      assert node.isParameterNode();
      writeParameterNodeLabel(out, node.asParameterNode());
    }
  }

  private void writeFieldNodeLabel(PrintStream out, FieldNode node) {
    out.print("\"");
    ProgramField field = node.getField();
    out.print(field.getHolderType().getSimpleName());
    out.print(".");
    out.print(field.getName().toSourceString());
    out.print("\"");
  }

  private void writeParameterNodeLabel(PrintStream out, ParameterNode node) {
    out.print("\"");
    ProgramMethod method = node.getMethod();
    out.print(method.getHolderType().getSimpleName());
    out.print(".");
    out.print(method.getName().toSourceString());
    out.print("(");
    out.print(node.getParameterIndex());
    out.print(")\"");
  }
}
