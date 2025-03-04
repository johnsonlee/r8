// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteArrayTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlowComparator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class InFlowPropagator {

  final AppView<AppInfoWithLiveness> appView;
  final Set<DexProgramClass> classesWithSingleCallerInlinedInstanceInitializers;
  final IRConverter converter;
  protected final FieldStateCollection fieldStates;
  final MethodStateCollectionByReference methodStates;
  final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  final InFlowComparator inFlowComparator;

  public InFlowPropagator(
      AppView<AppInfoWithLiveness> appView,
      Set<DexProgramClass> classesWithSingleCallerInlinedInstanceInitializers,
      IRConverter converter,
      FieldStateCollection fieldStates,
      MethodStateCollectionByReference methodStates,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      InFlowComparator inFlowComparator) {
    this.appView = appView;
    this.classesWithSingleCallerInlinedInstanceInitializers =
        classesWithSingleCallerInlinedInstanceInitializers;
    this.converter = converter;
    this.fieldStates = fieldStates;
    this.methodStates = methodStates;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.inFlowComparator = inFlowComparator;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    // Compute strongly connected components so that we can compute the fixpoint of multiple flow
    // graphs in parallel.
    List<FlowGraph> flowGraphs = computeStronglyConnectedFlowGraphs();
    assert InFlowPropagatorDebugUtils.setEnableLoggingBits(flowGraphs);
    processFlowGraphs(flowGraphs, executorService);

    // Account for the fact that fields that are read before they are written also needs to include
    // the default value in the field state. We only need to analyze if a given field is read before
    // it is written if the field has a non-trivial state in the flow graph. Therefore, we only
    // perform this analysis after having computed the initial fixpoint(s). The hypothesis is that
    // many fields will have reached the unknown state after the initial fixpoint, meaning there is
    // fewer fields to analyze.
    updateFieldStates(flowGraphs);
    Map<FlowGraph, Deque<FlowGraphNode>> worklists =
        includeDefaultValuesInFieldStates(flowGraphs, executorService);

    // Since the inclusion of default values changes the flow graphs, we need to repeat the
    // fixpoint.
    processWorklists(worklists, executorService);

    // The algorithm only changes the parameter states of each monomorphic method state. In case any
    // of these method states have effectively become unknown, we replace them by the canonicalized
    // unknown method state.
    postProcessMethodStates(executorService);

    // Copy the result of the flow graph propagation back to the field state collection.
    updateFieldStates(flowGraphs);
  }

  private List<FlowGraph> computeStronglyConnectedFlowGraphs() {
    // Build a graph with an edge from parameter p -> parameter p' if all argument information for p
    // must be included in the argument information for p'.
    FlowGraph flowGraph =
        FlowGraph.builder(appView, converter, fieldStates, methodStates, inFlowComparator)
            .addClasses()
            .clearInFlow()
            .build();
    List<Set<FlowGraphNode>> stronglyConnectedComponents =
        flowGraph.computeStronglyConnectedComponents();
    List<LinkedHashSet<FlowGraphNode>> stronglyConnectedComponentsWithDeterministicOrder =
        ListUtils.map(
            stronglyConnectedComponents,
            stronglyConnectedComponent ->
                (LinkedHashSet<FlowGraphNode>) stronglyConnectedComponent);
    return ListUtils.map(stronglyConnectedComponentsWithDeterministicOrder, FlowGraph::new);
  }

  private Map<FlowGraph, Deque<FlowGraphNode>> includeDefaultValuesInFieldStates(
      List<FlowGraph> flowGraphs, ExecutorService executorService) throws ExecutionException {
    DefaultFieldValueJoiner joiner = createDefaultFieldValueJoiner(flowGraphs);
    return joiner.joinDefaultFieldValuesForFieldsWithReadBeforeWrite(executorService);
  }

  protected DefaultFieldValueJoiner createDefaultFieldValueJoiner(List<FlowGraph> flowGraphs) {
    return new DefaultFieldValueJoiner(
        appView,
        classesWithSingleCallerInlinedInstanceInitializers,
        fieldStates,
        flowGraphs,
        immediateSubtypingInfo);
  }

  private void processFlowGraphs(List<FlowGraph> flowGraphs, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        flowGraphs, this::process, appView.options().getThreadingModule(), executorService);
  }

  private void processWorklists(
      Map<FlowGraph, Deque<FlowGraphNode>> worklists, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processMap(
        worklists, this::process, appView.options().getThreadingModule(), executorService);
  }

  private void process(FlowGraph flowGraph) {
    // Build a worklist containing all the nodes.
    Deque<FlowGraphNode> worklist = new ArrayDeque<>();
    flowGraph.forEachNode(worklist::add);
    process(flowGraph, worklist);
  }

  private void process(FlowGraph flowGraph, Deque<FlowGraphNode> worklist) {
    // Repeatedly propagate argument information through edges in the flow graph until there are no
    // more changes.
    // TODO(b/190154391): Consider a path p1 -> p2 -> p3 in the graph. If we process p2 first, then
    //  p3, and then p1, then the processing of p1 could cause p2 to change, which means that we
    //  need to reprocess p2 and then p3. If we always process leaves in the graph first, we would
    //  process p1, then p2, then p3, and then be done.
    while (!worklist.isEmpty()) {
      FlowGraphNode node = worklist.removeLast();
      node.unsetInWorklist();
      propagate(flowGraph, node, worklist);
    }
  }

  private void propagate(FlowGraph flowGraph, FlowGraphNode node, Deque<FlowGraphNode> worklist) {
    if (node.isBottom() || node.isUnused()) {
      return;
    }
    if (node.isUnknown()) {
      propagateUnknownNode(flowGraph, node, worklist);
    } else {
      propagateConcreteNode(flowGraph, node, worklist);
    }
  }

  private void propagateUnknownNode(
      FlowGraph flowGraph, FlowGraphNode node, Deque<FlowGraphNode> worklist) {
    assert !node.hasPredecessors();
    node.forEachSuccessor(
        (successorNode, transferFunctions) ->
            propagateUnknownStateToSuccessor(
                flowGraph, node, successorNode, transferFunctions, worklist));
    node.clearDanglingSuccessors();
  }

  private void propagateUnknownStateToSuccessor(
      FlowGraph flowGraph,
      FlowGraphNode node,
      FlowGraphNode successorNode,
      Set<AbstractFunction> transferFunctions,
      Deque<FlowGraphNode> worklist) {
    assert node.isUnknown();
    assert !successorNode.isUnknown();
    NonEmptyValueState stateToPropagate =
        narrowUnknownState(node, successorNode, transferFunctions);
    if (stateToPropagate.isUnknown()) {
      assert InFlowPropagatorDebugUtils.logPropagateUnknown(node, successorNode);
      successorNode.clearPredecessors(node);
      successorNode.setStateToUnknown();
      successorNode.addToWorkList(worklist);
    } else {
      boolean isSuccessorNodeUnknown =
          propagateConcreteStateToSuccessor(
              flowGraph,
              node,
              successorNode,
              stateToPropagate.asConcrete(),
              transferFunctions,
              worklist);
      if (isSuccessorNodeUnknown) {
        assert !successorNode.hasPredecessors();
      } else {
        successorNode.getPredecessors().remove(node);
      }
    }
  }

  private NonEmptyValueState narrowUnknownState(
      FlowGraphNode node, FlowGraphNode successorNode, Set<AbstractFunction> transferFunctions) {
    assert node.isUnknown();
    boolean narrowNullability =
        node.isReceiverNode() && Iterables.all(transferFunctions, AbstractFunction::isIdentity);
    boolean narrowType =
        node.getStaticType().isReferenceType()
            && node.getStaticType().isNotIdenticalTo(successorNode.getStaticType());
    if (!narrowNullability && !narrowType) {
      return ValueState.unknown();
    }
    Nullability narrowedNullability = narrowNullability ? definitelyNotNull() : maybeNull();
    if (node.getStaticType().isArrayType()) {
      return ConcreteArrayTypeValueState.create(narrowedNullability);
    } else {
      DynamicType narrowedType = node.getStaticType().toDynamicType(appView, narrowedNullability);
      return ConcreteClassTypeValueState.create(AbstractValue.unknown(), narrowedType);
    }
  }

  private void propagateConcreteNode(
      FlowGraph flowGraph, FlowGraphNode node, Deque<FlowGraphNode> worklist) {
    ConcreteValueState state = node.getState().asConcrete();
    node.removeSuccessorIf(
        (successorNode, transferFunctions) ->
            propagateConcreteStateToSuccessor(
                flowGraph, node, successorNode, state, transferFunctions, worklist));
  }

  private boolean propagateConcreteStateToSuccessor(
      FlowGraph flowGraph,
      FlowGraphNode node,
      FlowGraphNode successorNode,
      ConcreteValueState state,
      Set<AbstractFunction> transferFunctions,
      Deque<FlowGraphNode> worklist) {
    ConcreteValueState stateToPropagate = narrowConcreteState(node, successorNode, state);
    assert !successorNode.isUnknown();
    for (AbstractFunction transferFunction : transferFunctions) {
      FlowGraphStateProvider flowGraphStateProvider =
          FlowGraphStateProvider.create(flowGraph, transferFunction);
      ValueState transferState =
          transferFunction.apply(
              appView, flowGraphStateProvider, stateToPropagate, successorNode.getStaticType());
      ValueState oldSuccessorStateForDebugging =
          successorNode.getDebug() ? successorNode.getState().mutableCopy() : null;
      if (transferState.isBottom() || transferState.isUnused()) {
        // Nothing to propagate.
      } else if (transferState.isUnknown()) {
        successorNode.setStateToUnknown();
        successorNode.addToWorkList(worklist);
      } else {
        ConcreteValueState inState = transferState.asConcrete();
        DexType inStaticType = transferFunction.isIdentity() ? node.getStaticType() : null;
        successorNode.addState(
            appView, inState, inStaticType, () -> successorNode.addToWorkList(worklist));
      }

      assert InFlowPropagatorDebugUtils.logPropagateConcrete(
          node,
          successorNode,
          state,
          transferFunction,
          transferState,
          oldSuccessorStateForDebugging,
          flowGraphStateProvider);

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
  }

  private ConcreteValueState narrowConcreteState(
      FlowGraphNode node, FlowGraphNode successorNode, ConcreteValueState state) {
    // We don't track the dynamic type of array types and primitive types.
    if (state.isArrayState() || state.isPrimitiveState()) {
      return state;
    }

    TypeElement defaultType = successorNode.getStaticType().toTypeElement(appView);
    Nullability narrowedNullability =
        node.isReceiverNode() ? definitelyNotNull() : state.asReferenceState().getNullability();
    TypeElement narrowedType = node.getStaticType().toTypeElement(appView, narrowedNullability);
    if (!defaultType.equals(narrowedType)) {
      return state;
    }

    DynamicType dynamicTypeToPropagate = DynamicType.create(appView, narrowedType);
    if (state.isClassState()) {
      return state.asClassState().withDynamicType(dynamicTypeToPropagate);
    } else {
      return state.asReceiverState().withDynamicType(dynamicTypeToPropagate);
    }
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
    } else {
      // Replace unused parameters by the constant null.
      for (int i = 0; i < monomorphicMethodState.getParameterStates().size(); i++) {
        ValueState parameterState = monomorphicMethodState.getParameterState(i);
        if (parameterState.isUnused()) {
          DexType type = method.getArgumentType(i);
          assert parameterState.identical(ValueState.unused(type));
          ConcreteValueState replacement;
          if (type.isArrayType()) {
            replacement = new ConcreteArrayTypeValueState(Nullability.definitelyNull());
          } else if (type.isClassType()) {
            replacement =
                new ConcreteClassTypeValueState(
                    appView.abstractValueFactory().createUncheckedNullValue(),
                    DynamicType.definitelyNull());
          } else {
            assert type.isPrimitiveType();
            replacement =
                new ConcretePrimitiveTypeValueState(
                    appView.abstractValueFactory().createZeroValue());
          }
          monomorphicMethodState.setParameterState(i, replacement);
        } else {
          assert !parameterState.isConcrete() || !parameterState.asConcrete().isEffectivelyUnused();
        }
      }
    }
  }

  private void updateFieldStates(Collection<FlowGraph> flowGraphs) {
    for (FlowGraph flowGraph : flowGraphs) {
      flowGraph.forEachFieldNode(
          node -> {
            ProgramField field = node.getField();
            ValueState state = node.getState();
            ValueState previousState = fieldStates.set(field, state);
            assert state.isUnknown()
                    || state.identical(previousState)
                    || (state.isConcrete() && previousState.isBottom())
                : "Expected current state to be >= previous state";
          });
    }
  }
}
