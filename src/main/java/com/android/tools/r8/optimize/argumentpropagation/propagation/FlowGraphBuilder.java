// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.graph.ProgramField.asProgramFieldOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlowComparator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class FlowGraphBuilder {

  private final AppView<AppInfoWithLiveness> appView;
  private final IRConverter converter;
  private final FieldStateCollection fieldStates;
  private final MethodStateCollectionByReference methodStates;
  private final InFlowComparator inFlowComparator;

  private final LinkedHashMap<DexField, FlowGraphFieldNode> fieldNodes = new LinkedHashMap<>();
  private final LinkedHashMap<DexMethod, Int2ReferenceMap<FlowGraphParameterNode>> parameterNodes =
      new LinkedHashMap<>();

  public FlowGraphBuilder(
      AppView<AppInfoWithLiveness> appView,
      IRConverter converter,
      FieldStateCollection fieldStates,
      MethodStateCollectionByReference methodStates,
      InFlowComparator inFlowComparator) {
    this.appView = appView;
    this.converter = converter;
    this.fieldStates = fieldStates;
    this.methodStates = methodStates;
    this.inFlowComparator = inFlowComparator;
  }

  public FlowGraphBuilder addClasses() {
    appView.appInfo().classesWithDeterministicOrder().forEach(this::add);
    inFlowComparator.clear();
    return this;
  }

  public FlowGraphBuilder clearInFlow() {
    appView.appInfo().classes().forEach(this::clearInFlow);
    return this;
  }

  public FlowGraph build() {
    return new FlowGraph(fieldNodes, parameterNodes);
  }

  private void add(DexProgramClass clazz) {
    clazz.forEachProgramField(this::addField);
    clazz.forEachProgramMethod(this::addMethodParameters);
  }

  private void addField(ProgramField field) {
    ValueState fieldState = fieldStates.get(field);

    // No need to create nodes for fields with no in-flow or no useful information.
    if (fieldState.isBottom() || fieldState.isUnknown()) {
      return;
    }

    ConcreteValueState concreteFieldState = fieldState.asConcrete();

    // No need to create a node for a field that doesn't depend on any other nodes, unless some
    // other node depends on this field, in which case that other node will lead to creation of a
    // node for the current field.
    if (!concreteFieldState.hasInFlow()) {
      return;
    }

    FlowGraphFieldNode node = getOrCreateFieldNode(field, concreteFieldState);
    List<InFlow> inFlowWithDeterministicOrder =
        ListUtils.sort(concreteFieldState.getInFlow(), inFlowComparator);
    for (InFlow inFlow : inFlowWithDeterministicOrder) {
      if (addInFlow(inFlow, node).shouldBreak()) {
        assert node.isUnknown();
        break;
      }
    }
  }

  private void addMethodParameters(ProgramMethod method) {
    MethodState methodState = methodStates.get(method);

    // No need to create nodes for parameters with no in-flow or no useful information.
    if (methodState.isBottom() || methodState.isUnknown()) {
      return;
    }

    // Add nodes for the parameters for which we have non-trivial information.
    ConcreteMonomorphicMethodState monomorphicMethodState = methodState.asMonomorphic();
    List<ValueState> parameterStates = monomorphicMethodState.getParameterStates();
    for (int parameterIndex = 0; parameterIndex < parameterStates.size(); parameterIndex++) {
      ValueState parameterState = parameterStates.get(parameterIndex);
      addMethodParameter(method, parameterIndex, monomorphicMethodState, parameterState);
    }
  }

  private void addMethodParameter(
      ProgramMethod method,
      int parameterIndex,
      ConcreteMonomorphicMethodState methodState,
      ValueState parameterState) {
    // No need to create nodes for parameters with no in-parameters and parameters we don't know
    // anything about.
    if (parameterState.isBottom() || parameterState.isUnknown() || parameterState.isUnused()) {
      return;
    }

    ConcreteValueState concreteParameterState = parameterState.asConcrete();

    // No need to create a node for a parameter that doesn't depend on any other parameters,
    // unless some other node depends on this parameter, in which case that other node will lead
    // to the creation of a node for the current parameter.
    if (!concreteParameterState.hasInFlow()) {
      return;
    }

    FlowGraphParameterNode node = getOrCreateParameterNode(method, parameterIndex, methodState);
    List<InFlow> inFlowWithDeterministicOrder =
        ListUtils.sort(concreteParameterState.getInFlow(), inFlowComparator);
    for (InFlow inFlow : inFlowWithDeterministicOrder) {
      if (addInFlow(inFlow, node).shouldBreak()) {
        assert node.isUnknown();
        break;
      }
    }
  }

  // Returns BREAK if the current node has been set to unknown.
  private TraversalContinuation<?, ?> addInFlow(InFlow inFlow, FlowGraphNode node) {
    if (inFlow.isFieldValue()) {
      return addInFlow(inFlow.asFieldValue(), node);
    } else if (inFlow.isMethodParameter()) {
      return addInFlow(inFlow.asMethodParameter(), node);
    } else if (inFlow.isAbstractFunction()) {
      return addInFlow(inFlow.asAbstractFunction(), node);
    } else {
      throw new Unreachable(inFlow.getClass().getTypeName());
    }
  }

  private TraversalContinuation<?, ?> addInFlow(AbstractFunction inFlow, FlowGraphNode node) {
    return inFlow.traverseBaseInFlow(
        baseInFlow -> {
          if (baseInFlow.isFieldValue()) {
            return addInFlow(baseInFlow.asFieldValue(), node, inFlow);
          } else {
            assert baseInFlow.isMethodParameter();
            return addInFlow(baseInFlow.asMethodParameter(), node, inFlow);
          }
        });
  }

  private <TB, TC> TraversalContinuation<TB, TC> addInFlow(FieldValue inFlow, FlowGraphNode node) {
    return addInFlow(inFlow, node, AbstractFunction.identity());
  }

  private <TB, TC> TraversalContinuation<TB, TC> addInFlow(
      FieldValue inFlow, FlowGraphNode node, AbstractFunction transferFunction) {
    assert !node.isUnknown();

    ProgramField field = asProgramFieldOrNull(appView.definitionFor(inFlow.getField()));
    if (field == null) {
      assert false;
      return TraversalContinuation.doContinue();
    }

    ValueState fieldState = getFieldState(field, fieldStates);
    if (fieldState.isUnknown() && field.getType().isIdenticalTo(node.getStaticType())) {
      // The current node depends on a field for which we don't know anything.
      node.clearPredecessors();
      node.setStateToUnknown();
      return TraversalContinuation.doBreak();
    }

    FlowGraphFieldNode fieldNode = getOrCreateFieldNode(field, fieldState);
    node.addPredecessor(fieldNode, transferFunction);
    return TraversalContinuation.doContinue();
  }

  private TraversalContinuation<?, ?> addInFlow(MethodParameter inFlow, FlowGraphNode node) {
    return addInFlow(inFlow, node, AbstractFunction.identity());
  }

  private TraversalContinuation<?, ?> addInFlow(
      MethodParameter inFlow, FlowGraphNode node, AbstractFunction transferFunction) {
    ProgramMethod enclosingMethod = getEnclosingMethod(inFlow);
    if (enclosingMethod == null) {
      // This is a parameter of a single caller inlined method. Since this method has been
      // pruned, the call from inside the method no longer exists, and we can therefore safely
      // skip it.
      assert converter.getInliner().verifyIsPrunedDueToSingleCallerInlining(inFlow.getMethod());
      return TraversalContinuation.doContinue();
    }

    MethodState enclosingMethodState = getMethodState(enclosingMethod, methodStates);
    if (enclosingMethodState.isBottom()) {
      // The current node takes a value from a dead method; no need to propagate any information
      // from the dead assignment.
      return TraversalContinuation.doContinue();
    }

    assert enclosingMethodState.isMonomorphic() || enclosingMethodState.isUnknown();

    if (enclosingMethodState.isUnknown() && inFlow.getType().isIdenticalTo(node.getStaticType())) {
      // The current node depends on a parameter for which we don't know anything.
      node.clearPredecessors();
      node.setStateToUnknown();
      return TraversalContinuation.doBreak();
    }

    FlowGraphParameterNode predecessor =
        getOrCreateParameterNode(enclosingMethod, inFlow.getIndex(), enclosingMethodState);
    node.addPredecessor(predecessor, transferFunction);
    return TraversalContinuation.doContinue();
  }

  private void clearInFlow(DexProgramClass clazz) {
    clazz.forEachProgramField(this::clearInFlow);
    clazz.forEachProgramMethod(this::clearInFlow);
  }

  private void clearInFlow(ProgramField field) {
    ConcreteValueState concreteFieldState = fieldStates.get(field).asConcrete();
    if (concreteFieldState == null) {
      return;
    }
    ValueState concreteFieldStateOrBottom = concreteFieldState.clearInFlow();
    if (concreteFieldStateOrBottom.isBottom()) {
      fieldStates.remove(field);
      FlowGraphFieldNode node = getFieldNode(field);
      if (node != null && !node.getState().isUnknown()) {
        assert node.getState().identical(concreteFieldState);
        node.setState(concreteFieldStateOrBottom);
      }
    }
  }

  private void clearInFlow(ProgramMethod method) {
    ConcreteMonomorphicMethodState methodState = methodStates.get(method).asMonomorphic();
    if (methodState != null) {
      for (int i = 0; i < methodState.getParameterStates().size(); i++) {
        ValueState parameterState = methodState.getParameterState(i);
        ConcreteValueState concreteParameterState = parameterState.asConcrete();
        if (concreteParameterState != null) {
          ValueState concreteParameterStateOrBottom = concreteParameterState.clearInFlow();
          FlowGraphParameterNode node = getParameterNode(method, i);
          if (node != null && !node.getState().isUnknown()) {
            assert node.getState().identical(concreteParameterState);
            node.setState(concreteParameterStateOrBottom);
          }
        }
      }
    }
  }

  private FlowGraphFieldNode getFieldNode(ProgramField field) {
    return fieldNodes.get(field.getReference());
  }

  private FlowGraphFieldNode getOrCreateFieldNode(ProgramField field, ValueState fieldState) {
    return fieldNodes.computeIfAbsent(
        field.getReference(), ignoreKey(() -> new FlowGraphFieldNode(field, fieldState)));
  }

  private FlowGraphParameterNode getParameterNode(ProgramMethod method, int parameterIndex) {
    return parameterNodes
        .getOrDefault(method.getReference(), Int2ReferenceMaps.emptyMap())
        .get(parameterIndex);
  }

  private FlowGraphParameterNode getOrCreateParameterNode(
      ProgramMethod method, int parameterIndex, MethodState methodState) {
    Int2ReferenceMap<FlowGraphParameterNode> parameterNodesForMethod =
        parameterNodes.computeIfAbsent(
            method.getReference(), ignoreKey(Int2ReferenceOpenHashMap::new));
    return parameterNodesForMethod.compute(
        parameterIndex,
        (ignore, parameterNode) ->
            parameterNode != null
                ? parameterNode
                : new FlowGraphParameterNode(
                    method, methodState, parameterIndex, method.getArgumentType(parameterIndex)));
  }

  private ProgramMethod getEnclosingMethod(MethodParameter methodParameter) {
    DexMethod methodReference = methodParameter.getMethod();
    return methodReference.lookupOnProgramClass(
        asProgramClassOrNull(appView.definitionFor(methodParameter.getMethod().getHolderType())));
  }

  private ValueState getFieldState(ProgramField field, FieldStateCollection fieldStates) {
    if (field == null) {
      // Conservatively return unknown if for some reason we can't find the field.
      assert false;
      return ValueState.unknown();
    }
    return fieldStates.get(field);
  }

  private MethodState getMethodState(
      ProgramMethod method, MethodStateCollectionByReference methodStates) {
    if (method == null) {
      // Conservatively return unknown if for some reason we can't find the method.
      assert false;
      return MethodState.unknown();
    }
    return methodStates.get(method);
  }
}
