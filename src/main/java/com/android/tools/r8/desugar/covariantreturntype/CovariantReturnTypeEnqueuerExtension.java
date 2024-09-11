// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.covariantreturntype;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.errors.NonKeptMethodWithCovariantReturnTypeAnnotationDiagnostic;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FixpointEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveMethodEnqueuerAnalysis;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.shaking.KeepInfo;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class CovariantReturnTypeEnqueuerExtension
    implements NewlyLiveMethodEnqueuerAnalysis, FixpointEnqueuerAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final CovariantReturnTypeAnnotationTransformer transformer;

  private final Map<DexProgramClass, List<ProgramMethod>> pendingCovariantReturnTypeDesugaring =
      new IdentityHashMap<>();

  public CovariantReturnTypeEnqueuerExtension(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.transformer = new CovariantReturnTypeAnnotationTransformer(appView);
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      EnqueuerAnalysisCollection.Builder builder) {
    if (enqueuer.getMode().isInitialTreeShaking()
        && CovariantReturnTypeAnnotationTransformer.shouldRun(appView)) {
      CovariantReturnTypeEnqueuerExtension analysis =
          new CovariantReturnTypeEnqueuerExtension(appView);
      builder.addNewlyLiveMethodAnalysis(analysis).addFixpointAnalysis(analysis);
    }
  }

  @Override
  public void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {
    if (hasCovariantReturnTypeAnnotation(method)) {
      pendingCovariantReturnTypeDesugaring
          .computeIfAbsent(method.getHolder(), ignoreKey(ArrayList::new))
          .add(method);
    }
  }

  private boolean hasCovariantReturnTypeAnnotation(ProgramMethod method) {
    CovariantReturnTypeReferences references = transformer.getReferences();
    DexAnnotationSet annotations = method.getAnnotations();
    return annotations.hasAnnotation(
        annotation ->
            references.isOneOfCovariantReturnTypeAnnotations(annotation.getAnnotationType()));
  }

  @Override
  public void notifyFixpoint(
      Enqueuer enqueuer, EnqueuerWorklist worklist, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (pendingCovariantReturnTypeDesugaring.isEmpty()) {
      return;
    }
    ProgramMethodMap<Diagnostic> errors = ProgramMethodMap.createConcurrent();
    transformer.processMethods(
        pendingCovariantReturnTypeDesugaring,
        (bridge, target) -> {
          KeepMethodInfo.Joiner bridgeKeepInfo =
              getKeepInfoForCovariantReturnTypeBridge(target, errors);
          enqueuer.getKeepInfo().registerCompilerSynthesizedMethod(bridge);
          enqueuer.applyMinimumKeepInfoWhenLiveOrTargeted(bridge, bridgeKeepInfo);
          enqueuer.getProfileCollectionAdditions().addMethodIfContextIsInProfile(bridge, target);
        },
        executorService);
    errors.forEachValue(appView.reporter()::error);
    pendingCovariantReturnTypeDesugaring.clear();
  }

  private KeepMethodInfo.Joiner getKeepInfoForCovariantReturnTypeBridge(
      ProgramMethod target, ProgramMethodMap<Diagnostic> errors) {
    KeepInfo.Joiner<?, ?, ?> targetKeepInfo =
        appView
            .rootSet()
            .getDependentMinimumKeepInfo()
            .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
            .getOrDefault(target.getReference(), null);
    if (targetKeepInfo == null) {
      targetKeepInfo = KeepMethodInfo.newEmptyJoiner();
    }
    InternalOptions options = appView.options();
    if ((options.isMinifying() && targetKeepInfo.isMinificationAllowed())
        || (options.isOptimizing() && targetKeepInfo.isOptimizationAllowed())
        || (options.isShrinking() && targetKeepInfo.isShrinkingAllowed())) {
      errors.computeIfAbsent(target, NonKeptMethodWithCovariantReturnTypeAnnotationDiagnostic::new);
    }
    return targetKeepInfo.asMethodJoiner();
  }
}
