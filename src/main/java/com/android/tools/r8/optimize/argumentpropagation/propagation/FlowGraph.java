// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlowComparator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.optimize.argumentpropagation.utils.BidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.WorkList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FlowGraph extends BidirectedGraph<FlowGraphNode> implements FlowGraphStateProvider {

  private final LinkedHashMap<DexField, FlowGraphFieldNode> fieldNodes;
  private final LinkedHashMap<DexMethod, Int2ReferenceMap<FlowGraphParameterNode>> parameterNodes;

  public FlowGraph(
      LinkedHashMap<DexField, FlowGraphFieldNode> fieldNodes,
      LinkedHashMap<DexMethod, Int2ReferenceMap<FlowGraphParameterNode>> parameterNodes) {
    this.fieldNodes = fieldNodes;
    this.parameterNodes = parameterNodes;
  }

  public FlowGraph(LinkedHashSet<FlowGraphNode> nodes) {
    this(new LinkedHashMap<>(), new LinkedHashMap<>());
    for (FlowGraphNode node : nodes) {
      if (node.isFieldNode()) {
        FlowGraphFieldNode fieldNode = node.asFieldNode();
        fieldNodes.put(fieldNode.getField().getReference(), fieldNode);
      } else {
        FlowGraphParameterNode parameterNode = node.asParameterNode();
        parameterNodes
            .computeIfAbsent(
                parameterNode.getMethod().getReference(), ignoreKey(Int2ReferenceOpenHashMap::new))
            .put(parameterNode.getParameterIndex(), parameterNode);
      }
    }
  }

  public static FlowGraphBuilder builder(
      AppView<AppInfoWithLiveness> appView,
      IRConverter converter,
      FieldStateCollection fieldStates,
      MethodStateCollectionByReference methodStates,
      InFlowComparator inFlowComparator) {
    return new FlowGraphBuilder(appView, converter, fieldStates, methodStates, inFlowComparator);
  }

  @Override
  public Set<FlowGraphNode> computeStronglyConnectedComponent(FlowGraphNode node) {
    return computeStronglyConnectedComponent(node, WorkList.newWorkList(new LinkedHashSet<>()));
  }

  public void forEachFieldNode(Consumer<? super FlowGraphFieldNode> consumer) {
    fieldNodes.values().forEach(consumer);
  }

  public void forEachParameterNode(Consumer<? super FlowGraphParameterNode> consumer) {
    parameterNodes
        .values()
        .forEach(parameterNodesForMethod -> parameterNodesForMethod.values().forEach(consumer));
  }

  @Override
  public void forEachNeighbor(FlowGraphNode node, Consumer<? super FlowGraphNode> consumer) {
    node.getPredecessors().forEach(consumer);
    node.getSuccessors().forEach(consumer);
  }

  @Override
  public void forEachNode(Consumer<? super FlowGraphNode> consumer) {
    forEachFieldNode(consumer);
    parameterNodes.values().forEach(nodesForMethod -> nodesForMethod.values().forEach(consumer));
  }

  @Override
  public ValueState getState(DexField field) {
    return fieldNodes.get(field).getState();
  }

  @Override
  public ValueState getState(
      MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider) {
    FlowGraphParameterNode parameterNode =
        parameterNodes
            .getOrDefault(methodParameter.getMethod(), Int2ReferenceMaps.emptyMap())
            .get(methodParameter.getIndex());
    if (parameterNode != null) {
      return parameterNode.getState();
    }
    return defaultStateProvider.get();
  }
}
