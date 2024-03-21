// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.optimize.argumentpropagation.utils.BidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FlowGraph extends BidirectedGraph<FlowGraphNode> implements FlowGraphStateProvider {

  private final Map<DexField, FlowGraphFieldNode> fieldNodes;
  private final Map<DexMethod, Int2ReferenceMap<FlowGraphParameterNode>> parameterNodes;

  public FlowGraph(
      Map<DexField, FlowGraphFieldNode> fieldNodes,
      Map<DexMethod, Int2ReferenceMap<FlowGraphParameterNode>> parameterNodes) {
    this.fieldNodes = fieldNodes;
    this.parameterNodes = parameterNodes;
  }

  public FlowGraph(Collection<FlowGraphNode> nodes) {
    this(new IdentityHashMap<>(), new IdentityHashMap<>());
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
      MethodStateCollectionByReference methodStates) {
    return new FlowGraphBuilder(appView, converter, fieldStates, methodStates);
  }

  public void forEachFieldNode(Consumer<? super FlowGraphFieldNode> consumer) {
    fieldNodes.values().forEach(consumer);
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
  public ValueState getState(BaseInFlow inFlow, Supplier<ValueState> defaultStateProvider) {
    if (inFlow.isFieldValue()) {
      FieldValue fieldValue = inFlow.asFieldValue();
      return getState(fieldValue.getField());
    } else {
      assert inFlow.isMethodParameter();
      MethodParameter methodParameter = inFlow.asMethodParameter();
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
}
