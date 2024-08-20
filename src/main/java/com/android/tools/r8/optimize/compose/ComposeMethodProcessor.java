// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import static com.android.tools.r8.graph.ProgramField.asProgramFieldOrNull;

import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.analysis.path.PathConstraintSupplier;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.AbstractValueSupplier;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.conversion.PrimaryR8IRConverter;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorCodeScanner;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorOptimizationInfoPopulator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlowComparator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.optimize.argumentpropagation.propagation.DefaultFieldValueJoiner;
import com.android.tools.r8.optimize.argumentpropagation.propagation.FlowGraph;
import com.android.tools.r8.optimize.argumentpropagation.propagation.InFlowPropagator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ComposeMethodProcessor extends MethodProcessor {

  private final AppView<AppInfoWithLiveness> appView;
  private final ArgumentPropagatorCodeScanner codeScanner;
  private final PrimaryR8IRConverter converter;

  private final Set<ComposableCallGraphNode> processed = Sets.newIdentityHashSet();

  public ComposeMethodProcessor(
      AppView<AppInfoWithLiveness> appView,
      ComposableCallGraph callGraph,
      PrimaryR8IRConverter converter) {
    this.appView = appView;
    this.codeScanner = new ArgumentPropagatorCodeScannerForComposableFunctions(appView, callGraph);
    this.converter = converter;
  }

  public Set<ComposableCallGraphNode> processWave(
      Set<ComposableCallGraphNode> wave, ExecutorService executorService)
      throws ExecutionException {
    ProcessorContext processorContext = appView.createProcessorContext();
    ThreadUtils.processItems(
        wave,
        node -> {
          assert !processed.contains(node);
          converter.processDesugaredMethod(
              node.getMethod(),
              OptimizationFeedback.getIgnoreFeedback(),
              this,
              processorContext.createMethodProcessingContext(node.getMethod()),
              MethodConversionOptions.forLirPhase(appView));
        },
        appView.options().getThreadingModule(),
        executorService);
    processed.addAll(wave);
    return optimizeComposableFunctionsCalledFromWave(wave, executorService);
  }

  private Set<ComposableCallGraphNode> optimizeComposableFunctionsCalledFromWave(
      Set<ComposableCallGraphNode> wave, ExecutorService executorService)
      throws ExecutionException {
    prepareForInFlowPropagator();

    InFlowComparator emptyComparator = InFlowComparator.builder().build();
    InFlowPropagator inFlowPropagator =
        new InFlowPropagator(
            appView,
            null,
            converter,
            codeScanner.getFieldStates(),
            codeScanner.getMethodStates(),
            emptyComparator) {

          @Override
          protected DefaultFieldValueJoiner createDefaultFieldValueJoiner(
              List<FlowGraph> flowGraphs) {
            return new DefaultFieldValueJoiner(appView, null, fieldStates, flowGraphs) {

              @Override
              protected Map<DexProgramClass, List<ProgramField>> getFieldsOfInterest() {
                // We do not rely on the optimization of any fields in the Composable optimization
                // pass.
                return Collections.emptyMap();
              }
            };
          }
        };
    inFlowPropagator.run(executorService);

    ArgumentPropagatorOptimizationInfoPopulator optimizationInfoPopulator =
        new ArgumentPropagatorOptimizationInfoPopulator(appView, null, null, null, null);
    Set<ComposableCallGraphNode> optimizedComposableFunctions = Sets.newIdentityHashSet();
    wave.forEach(
        node ->
            node.forEachComposableCallee(
                callee -> {
                  if (Iterables.all(callee.getCallers(), this::isProcessed)) {
                    optimizationInfoPopulator.setOptimizationInfo(
                        callee.getMethod(), ProgramMethodSet.empty(), getMethodState(callee));
                    // TODO(b/302483644): Only enqueue this callee if its optimization info changed.
                    optimizedComposableFunctions.add(callee);
                  }
                }));
    return optimizedComposableFunctions;
  }

  private void prepareForInFlowPropagator() {
    FieldStateCollection fieldStates = codeScanner.getFieldStates();

    // Set all field states to unknown since we are not guaranteed to have processes all field
    // writes.
    fieldStates.forEach(
        (field, fieldState) ->
            fieldStates.addTemporaryFieldState(
                appView, field, ValueState::unknown, Timing.empty()));

    // Widen all parameter states that have in-flow to unknown, except when the in-flow is an
    // update-changed-flags abstract function.
    MethodStateCollectionByReference methodStates = codeScanner.getMethodStates();
    methodStates.forEach(
        (method, methodState) -> {
          if (!methodState.isMonomorphic()) {
            assert methodState.isUnknown();
            return;
          }
          ConcreteMonomorphicMethodState monomorphicMethodState = methodState.asMonomorphic();
          for (int parameterIndex = 0;
              parameterIndex < monomorphicMethodState.size();
              parameterIndex++) {
            ValueState parameterState = monomorphicMethodState.getParameterState(parameterIndex);
            if (parameterState.isConcrete()) {
              ConcreteValueState concreteParameterState = parameterState.asConcrete();
              prepareParameterStateForInFlowPropagator(
                  method, monomorphicMethodState, parameterIndex, concreteParameterState);
            }
          }
        });
  }

  private void prepareParameterStateForInFlowPropagator(
      DexMethod method,
      ConcreteMonomorphicMethodState methodState,
      int parameterIndex,
      ConcreteValueState parameterState) {
    if (!parameterState.hasInFlow()) {
      return;
    }

    UpdateChangedFlagsAbstractFunction transferFunction = null;
    if (parameterState.getInFlow().size() == 1) {
      transferFunction =
          Iterables.getOnlyElement(parameterState.getInFlow())
              .asUpdateChangedFlagsAbstractFunction();
    }
    if (transferFunction == null) {
      methodState.setParameterState(parameterIndex, ValueState.unknown());
      return;
    }

    // This is a call to a composable function from a restart function.
    Iterable<BaseInFlow> baseInFlow = transferFunction.getBaseInFlow();
    assert Iterables.size(baseInFlow) == 1;
    BaseInFlow singleBaseInFlow = IterableUtils.first(baseInFlow);
    assert singleBaseInFlow.isFieldValue();

    ProgramField field =
        asProgramFieldOrNull(appView.definitionFor(singleBaseInFlow.asFieldValue().getField()));
    assert field != null;

    // If the only input to the $$changed parameter of the Composable function is in-flow then skip.
    if (methodState.getParameterState(parameterIndex).getAbstractValue(appView).isBottom()) {
      methodState.setParameterState(parameterIndex, ValueState.unknown());
      return;
    }

    codeScanner
        .getFieldStates()
        .addTemporaryFieldState(
            appView,
            field,
            () ->
                new ConcretePrimitiveTypeValueState(
                    MethodParameter.createStatic(method, parameterIndex)),
            Timing.empty());
  }

  private MethodState getMethodState(ComposableCallGraphNode node) {
    assert processed.containsAll(node.getCallers());
    return codeScanner.getMethodStates().get(node.getMethod());
  }

  public void scan(ProgramMethod method, IRCode code, Timing timing) {
    LazyBox<Map<Value, AbstractValue>> abstractValues =
        new LazyBox<>(() -> new SparseConditionalConstantPropagation(appView).analyze(code));
    AbstractValueSupplier abstractValueSupplier =
        value -> {
          AbstractValue abstractValue = abstractValues.computeIfAbsent().get(value);
          assert abstractValue != null;
          return abstractValue;
        };
    PathConstraintSupplier pathConstraintSupplier =
        new PathConstraintSupplier(appView, code, codeScanner.getMethodParameterFactory());
    codeScanner.scan(method, code, abstractValueSupplier, pathConstraintSupplier, timing);
  }

  public boolean isProcessed(ComposableCallGraphNode node) {
    return processed.contains(node);
  }

  @Override
  public CallSiteInformation getCallSiteInformation() {
    return CallSiteInformation.empty();
  }

  @Override
  public MethodProcessorEventConsumer getEventConsumer() {
    throw new Unreachable();
  }

  @Override
  public boolean isComposeMethodProcessor() {
    return true;
  }

  @Override
  public ComposeMethodProcessor asComposeMethodProcessor() {
    return this;
  }

  @Override
  public boolean isProcessedConcurrently(ProgramMethod method) {
    return false;
  }

  @Override
  public void scheduleDesugaredMethodForProcessing(ProgramMethod method) {
    throw new Unreachable();
  }

  @Override
  public boolean shouldApplyCodeRewritings(ProgramMethod method) {
    return false;
  }
}
