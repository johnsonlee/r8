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
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteArrayTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.StateCloner;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.optimize.argumentpropagation.utils.BidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class InFlowPropagator {

  final AppView<AppInfoWithLiveness> appView;
  final IRConverter converter;
  final FieldStateCollection fieldStates;
  final MethodStateCollectionByReference methodStates;

  public InFlowPropagator(
      AppView<AppInfoWithLiveness> appView,
      IRConverter converter,
      FieldStateCollection fieldStates,
      MethodStateCollectionByReference methodStates) {
    this.appView = appView;
    this.converter = converter;
    this.fieldStates = fieldStates;
    this.methodStates = methodStates;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    // Compute strongly connected components so that we can compute the fixpoint of multiple flow
    // graphs in parallel.
    List<FlowGraph> flowGraphs = computeStronglyConnectedFlowGraphs();
    processFlowGraphs(flowGraphs, executorService);

    // Account for the fact that fields that are read before they are written also needs to include
    // the default value in the field state. We only need to analyze if a given field is read before
    // it is written if the field has a non-trivial state in the flow graph. Therefore, we only
    // perform this analysis after having computed the initial fixpoint(s). The hypothesis is that
    // many fields will have reached the unknown state after the initial fixpoint, meaning there is
    // fewer fields to analyze.
    Map<FlowGraph, Deque<Node>> worklists =
        includeDefaultValuesInFieldStates(flowGraphs, executorService);

    // Since the inclusion of default values changes the flow graphs, we need to repeat the
    // fixpoint.
    processWorklists(worklists, executorService);

    // The algorithm only changes the parameter states of each monomorphic method state. In case any
    // of these method states have effectively become unknown, we replace them by the canonicalized
    // unknown method state.
    postProcessMethodStates(executorService);
  }

  private List<FlowGraph> computeStronglyConnectedFlowGraphs() {
    // Build a graph with an edge from parameter p -> parameter p' if all argument information for p
    // must be included in the argument information for p'.
    FlowGraph flowGraph = new FlowGraph(appView.appInfo().classes());
    List<Set<Node>> stronglyConnectedComponents = flowGraph.computeStronglyConnectedComponents();
    return ListUtils.map(stronglyConnectedComponents, FlowGraph::new);
  }

  private Map<FlowGraph, Deque<Node>> includeDefaultValuesInFieldStates(
      List<FlowGraph> flowGraphs, ExecutorService executorService) throws ExecutionException {
    DefaultFieldValueJoiner joiner = new DefaultFieldValueJoiner(appView, flowGraphs);
    return joiner.joinDefaultFieldValuesForFieldsWithReadBeforeWrite(executorService);
  }

  private void processFlowGraphs(List<FlowGraph> flowGraphs, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        flowGraphs, this::process, appView.options().getThreadingModule(), executorService);
  }

  private void processWorklists(
      Map<FlowGraph, Deque<Node>> worklists, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processMap(
        worklists, this::process, appView.options().getThreadingModule(), executorService);
  }

  private void process(FlowGraph flowGraph) {
    // Build a worklist containing all the nodes.
    Deque<Node> worklist = new ArrayDeque<>();
    flowGraph.forEachNode(worklist::add);
    process(flowGraph, worklist);
  }

  private void process(FlowGraph flowGraph, Deque<Node> worklist) {
    // Repeatedly propagate argument information through edges in the flow graph until there are no
    // more changes.
    // TODO(b/190154391): Consider a path p1 -> p2 -> p3 in the graph. If we process p2 first, then
    //  p3, and then p1, then the processing of p1 could cause p2 to change, which means that we
    //  need to reprocess p2 and then p3. If we always process leaves in the graph first, we would
    //  process p1, then p2, then p3, and then be done.
    while (!worklist.isEmpty()) {
      Node node = worklist.removeLast();
      node.unsetInWorklist();
      propagate(flowGraph, node, worklist);
    }
  }

  private void propagate(FlowGraph flowGraph, Node node, Deque<Node> worklist) {
    if (node.isBottom()) {
      return;
    }
    if (node.isUnknown()) {
      assert !node.hasPredecessors();
      for (Node successorNode : node.getSuccessors()) {
        assert !successorNode.isUnknown();
        successorNode.clearPredecessors(node);
        successorNode.setStateToUnknown();
        successorNode.addToWorkList(worklist);
      }
      node.clearDanglingSuccessors();
    } else {
      propagateNode(flowGraph, node, worklist);
    }
  }

  private void propagateNode(FlowGraph flowGraph, Node node, Deque<Node> worklist) {
    ConcreteValueState state = node.getState().asConcrete();
    node.removeSuccessorIf(
        (successorNode, transferFunctions) -> {
          assert !successorNode.isUnknown();
          for (AbstractFunction transferFunction : transferFunctions) {
            FlowGraphStateProvider flowGraphStateProvider =
                FlowGraphStateProvider.create(flowGraph, transferFunction);
            ValueState transferState = transferFunction.apply(flowGraphStateProvider, state);
            if (transferState.isBottom()) {
              // Nothing to propagate.
            } else if (transferState.isUnknown()) {
              successorNode.setStateToUnknown();
              successorNode.addToWorkList(worklist);
            } else {
              ConcreteValueState concreteTransferState = transferState.asConcrete();
              successorNode.addState(
                  appView, concreteTransferState, () -> successorNode.addToWorkList(worklist));
            }
            // If this successor has become unknown, there is no point in continuing to propagate
            // flow to it from any of its predecessors. We therefore clear the predecessors to
            // improve performance of the fixpoint computation.
            if (successorNode.isUnknown()) {
              successorNode.clearPredecessors(node);
              return true;
            }
            assert !successorNode.isEffectivelyUnknown();
          }
          return false;
        });
  }

  private void postProcessMethodStates(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        this::postProcessMethodStates,
        appView.options().getThreadingModule(),
        executorService);
  }

  private void postProcessMethodStates(DexProgramClass clazz) {
    clazz.forEachProgramMethod(this::postProcessMethodState);
  }

  private void postProcessMethodState(ProgramMethod method) {
    ConcreteMethodState methodState = methodStates.get(method).asConcrete();
    if (methodState == null) {
      return;
    }
    assert methodState.isMonomorphic();
    ConcreteMonomorphicMethodState monomorphicMethodState = methodState.asMonomorphic();
    if (monomorphicMethodState.isEffectivelyBottom()) {
      methodStates.set(method, MethodState.bottom());
    } else if (monomorphicMethodState.isEffectivelyUnknown()) {
      methodStates.set(method, MethodState.unknown());
    }
  }

  public class FlowGraph extends BidirectedGraph<Node> implements FlowGraphStateProvider {

    private final Map<DexField, FieldNode> fieldNodes = new IdentityHashMap<>();
    private final Map<DexMethod, Int2ReferenceMap<ParameterNode>> parameterNodes =
        new IdentityHashMap<>();

    public FlowGraph(Iterable<DexProgramClass> classes) {
      classes.forEach(this::add);
    }

    public FlowGraph(Collection<Node> nodes) {
      for (Node node : nodes) {
        if (node.isFieldNode()) {
          FieldNode fieldNode = node.asFieldNode();
          fieldNodes.put(fieldNode.getField().getReference(), fieldNode);
        } else {
          ParameterNode parameterNode = node.asParameterNode();
          parameterNodes
              .computeIfAbsent(
                  parameterNode.getMethod().getReference(),
                  ignoreKey(Int2ReferenceOpenHashMap::new))
              .put(parameterNode.getParameterIndex(), parameterNode);
        }
      }
    }

    public void forEachFieldNode(Consumer<? super FieldNode> consumer) {
      fieldNodes.values().forEach(consumer);
    }

    @Override
    public void forEachNeighbor(Node node, Consumer<? super Node> consumer) {
      node.getPredecessors().forEach(consumer);
      node.getSuccessors().forEach(consumer);
    }

    @Override
    public void forEachNode(Consumer<? super Node> consumer) {
      forEachFieldNode(consumer);
      parameterNodes.values().forEach(nodesForMethod -> nodesForMethod.values().forEach(consumer));
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

      FieldNode node = getOrCreateFieldNode(field, concreteFieldState);
      for (InFlow inFlow : concreteFieldState.getInFlow()) {
        if (addInFlow(inFlow, node).shouldBreak()) {
          assert node.isUnknown();
          break;
        }
      }

      if (!node.getState().isUnknown()) {
        assert node.getState() == concreteFieldState;
        node.setState(concreteFieldState.clearInFlow());
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
      if (parameterState.isBottom() || parameterState.isUnknown()) {
        return;
      }

      ConcreteValueState concreteParameterState = parameterState.asConcrete();

      // No need to create a node for a parameter that doesn't depend on any other parameters,
      // unless some other node depends on this parameter, in which case that other node will lead
      // to the creation of a node for the current parameter.
      if (!concreteParameterState.hasInFlow()) {
        return;
      }

      ParameterNode node = getOrCreateParameterNode(method, parameterIndex, methodState);
      for (InFlow inFlow : concreteParameterState.getInFlow()) {
        if (addInFlow(inFlow, node).shouldBreak()) {
          assert node.isUnknown();
          break;
        }
      }

      if (!node.getState().isUnknown()) {
        assert node.getState() == concreteParameterState;
        node.setState(concreteParameterState.clearInFlow());
      }
    }

    // Returns BREAK if the current node has been set to unknown.
    private TraversalContinuation<?, ?> addInFlow(InFlow inFlow, Node node) {
      if (inFlow.isAbstractFunction()) {
        return addInFlow(inFlow.asAbstractFunction(), node);
      } else if (inFlow.isFieldValue()) {
        return addInFlow(inFlow.asFieldValue(), node);
      } else if (inFlow.isMethodParameter()) {
        return addInFlow(inFlow.asMethodParameter(), node);
      } else {
        throw new Unreachable(inFlow.getClass().getTypeName());
      }
    }

    private TraversalContinuation<?, ?> addInFlow(AbstractFunction inFlow, Node node) {
      for (BaseInFlow baseInFlow : inFlow.getBaseInFlow()) {
        TraversalContinuation<?, ?> traversalContinuation;
        if (baseInFlow.isFieldValue()) {
          traversalContinuation = addInFlow(baseInFlow.asFieldValue(), node, inFlow);
        } else {
          assert baseInFlow.isMethodParameter();
          traversalContinuation = addInFlow(baseInFlow.asMethodParameter(), node, inFlow);
        }
        if (traversalContinuation.shouldBreak()) {
          return traversalContinuation;
        }
      }
      return TraversalContinuation.doContinue();
    }

    private TraversalContinuation<?, ?> addInFlow(FieldValue inFlow, Node node) {
      return addInFlow(inFlow, node, AbstractFunction.identity());
    }

    private TraversalContinuation<?, ?> addInFlow(
        FieldValue inFlow, Node node, AbstractFunction transferFunction) {
      assert !node.isUnknown();

      ProgramField field = asProgramFieldOrNull(appView.definitionFor(inFlow.getField()));
      if (field == null) {
        assert false;
        return TraversalContinuation.doContinue();
      }

      ValueState fieldState = getFieldState(field);
      if (fieldState.isUnknown()) {
        // The current node depends on a field for which we don't know anything.
        node.clearPredecessors();
        node.setStateToUnknown();
        return TraversalContinuation.doBreak();
      }

      FieldNode fieldNode = getOrCreateFieldNode(field, fieldState);
      node.addPredecessor(fieldNode, transferFunction);
      return TraversalContinuation.doContinue();
    }

    private TraversalContinuation<?, ?> addInFlow(MethodParameter inFlow, Node node) {
      return addInFlow(inFlow, node, AbstractFunction.identity());
    }

    private TraversalContinuation<?, ?> addInFlow(
        MethodParameter inFlow, Node node, AbstractFunction transferFunction) {
      ProgramMethod enclosingMethod = getEnclosingMethod(inFlow);
      if (enclosingMethod == null) {
        // This is a parameter of a single caller inlined method. Since this method has been
        // pruned, the call from inside the method no longer exists, and we can therefore safely
        // skip it.
        assert converter.getInliner().verifyIsPrunedDueToSingleCallerInlining(inFlow.getMethod());
        return TraversalContinuation.doContinue();
      }

      MethodState enclosingMethodState = getMethodState(enclosingMethod);
      if (enclosingMethodState.isBottom()) {
        // The current node takes a value from a dead method; no need to propagate any information
        // from the dead assignment.
        return TraversalContinuation.doContinue();
      }

      if (enclosingMethodState.isUnknown()) {
        // The current node depends on a parameter for which we don't know anything.
        node.clearPredecessors();
        node.setStateToUnknown();
        return TraversalContinuation.doBreak();
      }

      assert enclosingMethodState.isConcrete();
      assert enclosingMethodState.asConcrete().isMonomorphic();

      ParameterNode predecessor =
          getOrCreateParameterNode(
              enclosingMethod,
              inFlow.getIndex(),
              enclosingMethodState.asConcrete().asMonomorphic());
      node.addPredecessor(predecessor, transferFunction);
      return TraversalContinuation.doContinue();
    }

    private FieldNode getOrCreateFieldNode(ProgramField field, ValueState fieldState) {
      return fieldNodes.computeIfAbsent(
          field.getReference(), ignoreKey(() -> new FieldNode(field, fieldState)));
    }

    private ParameterNode getOrCreateParameterNode(
        ProgramMethod method, int parameterIndex, ConcreteMonomorphicMethodState methodState) {
      Int2ReferenceMap<ParameterNode> parameterNodesForMethod =
          parameterNodes.computeIfAbsent(
              method.getReference(), ignoreKey(Int2ReferenceOpenHashMap::new));
      return parameterNodesForMethod.compute(
          parameterIndex,
          (ignore, parameterNode) ->
              parameterNode != null
                  ? parameterNode
                  : new ParameterNode(
                      method, methodState, parameterIndex, method.getArgumentType(parameterIndex)));
    }

    private ProgramMethod getEnclosingMethod(MethodParameter methodParameter) {
      DexMethod methodReference = methodParameter.getMethod();
      return methodReference.lookupOnProgramClass(
          asProgramClassOrNull(appView.definitionFor(methodParameter.getMethod().getHolderType())));
    }

    private ValueState getFieldState(ProgramField field) {
      if (field == null) {
        // Conservatively return unknown if for some reason we can't find the field.
        assert false;
        return ValueState.unknown();
      }
      return fieldStates.get(field);
    }

    private MethodState getMethodState(ProgramMethod method) {
      if (method == null) {
        // Conservatively return unknown if for some reason we can't find the method.
        assert false;
        return MethodState.unknown();
      }
      return methodStates.get(method);
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
        ParameterNode parameterNode =
            parameterNodes
                .getOrDefault(methodParameter.getMethod(), Int2ReferenceMaps.emptyMap())
                .get(methodParameter.getIndex());
        if (parameterNode != null) {
          return parameterNode.getState();
        }
        assert verifyMissingParameterStateIsBottom(methodParameter);
        return defaultStateProvider.get();
      }
    }

    private boolean verifyMissingParameterStateIsBottom(MethodParameter methodParameter) {
      ProgramMethod enclosingMethod = getEnclosingMethod(methodParameter);
      if (enclosingMethod == null) {
        assert converter
            .getInliner()
            .verifyIsPrunedDueToSingleCallerInlining(methodParameter.getMethod());
        return true;
      }
      MethodState enclosingMethodState = getMethodState(enclosingMethod);
      return enclosingMethodState.isBottom();
    }
  }

  public abstract static class Node {

    private final Set<Node> predecessors = Sets.newIdentityHashSet();
    private final Map<Node, Set<AbstractFunction>> successors = new IdentityHashMap<>();

    private boolean inWorklist = true;

    void addState(
        AppView<AppInfoWithLiveness> appView,
        ConcreteValueState stateToAdd,
        Action onChangedAction) {
      ValueState oldState = getState();
      ValueState newState =
          oldState.mutableJoin(
              appView, stateToAdd, getStaticType(), StateCloner.getCloner(), onChangedAction);
      if (newState != oldState) {
        setState(newState);
        onChangedAction.execute();
      }
    }

    abstract ValueState getState();

    abstract DexType getStaticType();

    abstract void setState(ValueState valueState);

    void setStateToUnknown() {
      setState(ValueState.unknown());
    }

    void addPredecessor(Node predecessor, AbstractFunction abstractFunction) {
      predecessor.successors.computeIfAbsent(this, ignoreKey(HashSet::new)).add(abstractFunction);
      predecessors.add(predecessor);
    }

    void clearPredecessors() {
      for (Node predecessor : predecessors) {
        predecessor.successors.remove(this);
      }
      predecessors.clear();
    }

    void clearPredecessors(Node cause) {
      for (Node predecessor : predecessors) {
        if (predecessor != cause) {
          predecessor.successors.remove(this);
        }
      }
      predecessors.clear();
    }

    Set<Node> getPredecessors() {
      return predecessors;
    }

    boolean hasPredecessors() {
      return !predecessors.isEmpty();
    }

    void clearDanglingSuccessors() {
      successors.clear();
    }

    Set<Node> getSuccessors() {
      return successors.keySet();
    }

    public void forEachSuccessor(BiConsumer<Node, Set<AbstractFunction>> consumer) {
      successors.forEach(consumer);
    }

    public void removeSuccessorIf(BiPredicate<Node, Set<AbstractFunction>> predicate) {
      successors.entrySet().removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
    }

    boolean hasSuccessors() {
      return !successors.isEmpty();
    }

    boolean isBottom() {
      return getState().isBottom();
    }

    boolean isFieldNode() {
      return false;
    }

    FieldNode asFieldNode() {
      return null;
    }

    boolean isParameterNode() {
      return false;
    }

    ParameterNode asParameterNode() {
      return null;
    }

    boolean isEffectivelyUnknown() {
      return getState().isConcrete() && getState().asConcrete().isEffectivelyUnknown();
    }

    boolean isUnknown() {
      return getState().isUnknown();
    }

    // No need to enqueue the affected node if it is already in the worklist or if it does not have
    // any successors (i.e., the successor is a leaf).
    void addToWorkList(Deque<Node> worklist) {
      if (!inWorklist && hasSuccessors()) {
        worklist.add(this);
        inWorklist = true;
      }
    }

    void unsetInWorklist() {
      assert inWorklist;
      inWorklist = false;
    }
  }

  public static class FieldNode extends Node {

    private final ProgramField field;
    private ValueState fieldState;

    FieldNode(ProgramField field, ValueState fieldState) {
      this.field = field;
      this.fieldState = fieldState;
    }

    public ProgramField getField() {
      return field;
    }

    @Override
    DexType getStaticType() {
      return field.getType();
    }

    void addDefaultValue(AppView<AppInfoWithLiveness> appView, Action onChangedAction) {
      AbstractValueFactory abstractValueFactory = appView.abstractValueFactory();
      AbstractValue defaultValue;
      if (field.getAccessFlags().isStatic() && field.getDefinition().hasExplicitStaticValue()) {
        defaultValue = field.getDefinition().getStaticValue().toAbstractValue(abstractValueFactory);
      } else if (field.getType().isPrimitiveType()) {
        defaultValue = abstractValueFactory.createZeroValue();
      } else {
        defaultValue = abstractValueFactory.createUncheckedNullValue();
      }
      NonEmptyValueState fieldStateToAdd;
      if (field.getType().isArrayType()) {
        Nullability defaultNullability = Nullability.definitelyNull();
        fieldStateToAdd = ConcreteArrayTypeValueState.create(defaultNullability);
      } else if (field.getType().isClassType()) {
        assert defaultValue.isNull() || defaultValue.isSingleStringValue();
        DynamicType dynamicType =
            defaultValue.isNull()
                ? DynamicType.definitelyNull()
                : DynamicType.createExact(
                    TypeElement.stringClassType(appView, Nullability.definitelyNotNull()));
        fieldStateToAdd = ConcreteClassTypeValueState.create(defaultValue, dynamicType);
      } else {
        assert field.getType().isPrimitiveType();
        fieldStateToAdd = ConcretePrimitiveTypeValueState.create(defaultValue);
      }
      if (fieldStateToAdd.isConcrete()) {
        addState(appView, fieldStateToAdd.asConcrete(), onChangedAction);
      } else {
        // We should always be able to map static field values to an unknown abstract value.
        assert false;
        setStateToUnknown();
        onChangedAction.execute();
      }
    }

    @Override
    ValueState getState() {
      return fieldState;
    }

    @Override
    void setState(ValueState fieldState) {
      this.fieldState = fieldState;
    }

    @Override
    boolean isFieldNode() {
      return true;
    }

    @Override
    FieldNode asFieldNode() {
      return this;
    }
  }

  static class ParameterNode extends Node {

    private final ProgramMethod method;
    private final ConcreteMonomorphicMethodState methodState;
    private final int parameterIndex;
    private final DexType parameterType;

    ParameterNode(
        ProgramMethod method,
        ConcreteMonomorphicMethodState methodState,
        int parameterIndex,
        DexType parameterType) {
      this.method = method;
      this.methodState = methodState;
      this.parameterIndex = parameterIndex;
      this.parameterType = parameterType;
    }

    ProgramMethod getMethod() {
      return method;
    }

    int getParameterIndex() {
      return parameterIndex;
    }

    @Override
    DexType getStaticType() {
      return parameterType;
    }

    @Override
    ValueState getState() {
      return methodState.getParameterState(parameterIndex);
    }

    @Override
    void setState(ValueState parameterState) {
      methodState.setParameterState(parameterIndex, parameterState);
    }

    @Override
    boolean isParameterNode() {
      return true;
    }

    @Override
    ParameterNode asParameterNode() {
      return this;
    }
  }
}
