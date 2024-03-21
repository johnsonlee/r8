// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramFieldSet;
import com.google.common.collect.Lists;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class DefaultFieldValueJoiner {

  private final AppView<AppInfoWithLiveness> appView;
  private final List<FlowGraph> flowGraphs;

  public DefaultFieldValueJoiner(AppView<AppInfoWithLiveness> appView, List<FlowGraph> flowGraphs) {
    this.appView = appView;
    this.flowGraphs = flowGraphs;
  }

  public Map<FlowGraph, Deque<FlowGraphNode>> joinDefaultFieldValuesForFieldsWithReadBeforeWrite(
      ExecutorService executorService) throws ExecutionException {
    // Find all the fields where we need to determine if each field read is guaranteed to be
    // dominated by a write.
    Map<DexProgramClass, List<ProgramField>> fieldsOfInterest = getFieldsOfInterest();

    // If constructor inlining is disabled, then we focus on whether each instance initializer
    // definitely assigns the given field before it is read. We do the same for final and static
    // fields.
    Map<DexProgramClass, List<ProgramField>> nonFinalInstanceFields =
        removeFieldsNotSubjectToInitializerAnalysis(fieldsOfInterest);
    ProgramFieldSet fieldsWithLiveDefaultValue = ProgramFieldSet.createConcurrent();
    analyzeInstanceInitializers(fieldsOfInterest, fieldsWithLiveDefaultValue::add, executorService);

    // For non-final fields where writes in instance initializers may have been subject to
    // constructor inlining, we find all new-instance instructions (including subtype allocations)
    // and check if the field is written on each allocation before it is possibly read.
    analyzeNewInstanceInstructions(nonFinalInstanceFields, fieldsWithLiveDefaultValue::add);

    return updateFlowGraphs(fieldsWithLiveDefaultValue, executorService);
  }

  private Map<DexProgramClass, List<ProgramField>> getFieldsOfInterest() {
    Map<DexProgramClass, List<ProgramField>> fieldsOfInterest = new IdentityHashMap<>();
    for (FlowGraph flowGraph : flowGraphs) {
      // TODO(b/296030319): We only need to include the fields where including the default value
      //  would make a difference. Then we can assert below in updateFlowGraphs() that adding the
      //  default value changes the field state.
      flowGraph.forEachFieldNode(
          node -> {
            ProgramField field = node.getField();
            fieldsOfInterest
                .computeIfAbsent(field.getHolder(), ignoreKey(ArrayList::new))
                .add(field);
          });
    }
    return fieldsOfInterest;
  }

  private Map<DexProgramClass, List<ProgramField>> removeFieldsNotSubjectToInitializerAnalysis(
      Map<DexProgramClass, List<ProgramField>> fieldsOfInterest) {
    // When constructor inlining is disabled, we only analyze the initializers of each field holder.
    if (!appView.options().canInitNewInstanceUsingSuperclassConstructor()) {
      return Collections.emptyMap();
    }

    // When constructor inlining is enabled, we can still limit the analysis to the instance
    // initializers for final fields. We can do the same for static fields as <clinit> is not
    // subject to inlining.
    Map<DexProgramClass, List<ProgramField>> nonFinalInstanceFields = new IdentityHashMap<>();
    MapUtils.removeIf(
        fieldsOfInterest,
        (holder, fields) -> {
          fields.removeIf(
              field -> {
                if (!field.getAccessFlags().isFinal() && !field.getAccessFlags().isStatic()) {
                  nonFinalInstanceFields
                      .computeIfAbsent(holder, ignoreKey(ArrayList::new))
                      .add(field);
                }
                return false;
              });
          return fields.isEmpty();
        });
    return nonFinalInstanceFields;
  }

  private void analyzeInstanceInitializers(
      Map<DexProgramClass, List<ProgramField>> fieldsOfInterest,
      Consumer<ProgramField> concurrentLiveDefaultValueConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processMap(
        fieldsOfInterest,
        (clazz, fields) -> {
          ProgramFieldSet instanceFieldsWithLiveDefaultValue = ProgramFieldSet.create();
          ProgramFieldSet staticFieldsWithLiveDefaultValue = ProgramFieldSet.create();
          partitionFields(
              fields, instanceFieldsWithLiveDefaultValue, staticFieldsWithLiveDefaultValue);
          analyzeClassInitializerAssignments(
              clazz, staticFieldsWithLiveDefaultValue, concurrentLiveDefaultValueConsumer);
          analyzeInstanceInitializerAssignments(
              clazz, instanceFieldsWithLiveDefaultValue, concurrentLiveDefaultValueConsumer);
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private void partitionFields(
      Collection<ProgramField> fields,
      ProgramFieldSet instanceFieldsWithLiveDefaultValue,
      ProgramFieldSet staticFieldsWithLiveDefaultValue) {
    for (ProgramField field : fields) {
      if (field.getAccessFlags().isStatic()) {
        staticFieldsWithLiveDefaultValue.add(field);
      } else {
        instanceFieldsWithLiveDefaultValue.add(field);
      }
    }
  }

  private void analyzeClassInitializerAssignments(
      DexProgramClass clazz,
      ProgramFieldSet staticFieldsWithLiveDefaultValue,
      Consumer<ProgramField> liveDefaultValueConsumer) {
    if (staticFieldsWithLiveDefaultValue.isEmpty()) {
      return;
    }
    if (clazz.hasClassInitializer()) {
      IRCode code =
          clazz
              .getProgramClassInitializer()
              .buildIR(appView, MethodConversionOptions.nonConverting());
      FieldReadBeforeWriteAnalysis analysis = new FieldReadBeforeWriteAnalysis(appView, code);
      staticFieldsWithLiveDefaultValue.removeIf(analysis::isStaticFieldNeverReadBeforeWrite);
    }
    staticFieldsWithLiveDefaultValue.forEach(liveDefaultValueConsumer);
  }

  private void analyzeInstanceInitializerAssignments(
      DexProgramClass clazz,
      ProgramFieldSet instanceFieldsWithLiveDefaultValue,
      Consumer<ProgramField> liveDefaultValueConsumer) {
    if (instanceFieldsWithLiveDefaultValue.isEmpty()) {
      return;
    }
    List<ProgramMethod> instanceInitializers =
        Lists.newArrayList(clazz.programInstanceInitializers());
    // TODO(b/296030319): Handle multiple instance initializers.
    if (instanceInitializers.size() == 1) {
      ProgramMethod instanceInitializer = ListUtils.first(instanceInitializers);
      IRCode code = instanceInitializer.buildIR(appView, MethodConversionOptions.nonConverting());
      FieldReadBeforeWriteAnalysis analysis = new FieldReadBeforeWriteAnalysis(appView, code);
      instanceFieldsWithLiveDefaultValue.removeIf(analysis::isInstanceFieldNeverReadBeforeWrite);
    }
    instanceFieldsWithLiveDefaultValue.forEach(liveDefaultValueConsumer);
  }

  private void analyzeNewInstanceInstructions(
      Map<DexProgramClass, List<ProgramField>> nonFinalInstanceFields,
      Consumer<ProgramField> liveDefaultValueConsumer) {
    // Conservatively treat all fields as maybe read before written.
    // TODO(b/296030319): Implement analysis by building IR for all methods that instantiate the
    //  relevant classes and analyzing the puts to the newly created instances.
    for (ProgramField field : IterableUtils.flatten(nonFinalInstanceFields.values())) {
      liveDefaultValueConsumer.accept(field);
    }
  }

  private Map<FlowGraph, Deque<FlowGraphNode>> updateFlowGraphs(
      ProgramFieldSet fieldsWithLiveDefaultValue, ExecutorService executorService)
      throws ExecutionException {
    Collection<Pair<FlowGraph, Deque<FlowGraphNode>>> worklists =
        ThreadUtils.processItemsWithResultsThatMatches(
            flowGraphs,
            flowGraph -> {
              Deque<FlowGraphNode> worklist = new ArrayDeque<>();
              flowGraph.forEachFieldNode(
                  node -> {
                    ProgramField field = node.getField();
                    if (fieldsWithLiveDefaultValue.contains(field)) {
                      node.addDefaultValue(
                          appView,
                          () -> {
                            if (node.isUnknown()) {
                              node.clearPredecessors();
                            }
                            node.addToWorkList(worklist);
                          });
                    }
                  });
              return new Pair<>(flowGraph, worklist);
            },
            pair -> !pair.getSecond().isEmpty(),
            appView.options().getThreadingModule(),
            executorService);
    return MapUtils.newIdentityHashMap(
        builder -> worklists.forEach(pair -> builder.put(pair.getFirst(), pair.getSecond())));
  }
}
