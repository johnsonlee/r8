// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.DefaultEnqueuerUseRegistry;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;

public class ApiModelAnalysis
    implements NewlyFailedMethodResolutionEnqueuerAnalysis,
        NewlyLiveCodeEnqueuerAnalysis,
        NewlyLiveFieldEnqueuerAnalysis,
        NewlyLiveMethodEnqueuerAnalysis,
        NewlyLiveNonProgramClassEnqueuerAnalysis,
        NewlyReachableFieldEnqueuerAnalysis,
        NewlyTargetedMethodEnqueuerAnalysis {

  private final AppView<?> appView;
  private final AndroidApiLevelCompute apiCompute;
  private final ComputedApiLevel minApiLevel;

  public ApiModelAnalysis(AppView<?> appView) {
    this.appView = appView;
    this.apiCompute = appView.apiLevelCompute();
    this.minApiLevel = appView.computedMinApiLevel();
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      EnqueuerAnalysisCollection.Builder builder) {
    if (appView.options().apiModelingOptions().enableLibraryApiModeling) {
      ApiModelAnalysis analysis = new ApiModelAnalysis(appView);
      builder
          .addNewlyFailedMethodResolutionAnalysis(analysis)
          .addNewlyLiveCodeAnalysis(analysis)
          .addNewlyLiveFieldAnalysis(analysis)
          .addNewlyLiveMethodAnalysis(analysis)
          .addNewlyLiveNonProgramClassAnalysis(analysis)
          .addNewlyReachableFieldAnalysis(analysis)
          .addNewlyTargetedMethodAnalysis(analysis);
    }
  }

  @Override
  public void processNewlyLiveField(
      ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {
    computeAndSetApiLevelForDefinition(field);
  }

  @Override
  public void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {
    computeAndSetApiLevelForDefinition(method);
  }

  @Override
  public void processNewlyLiveCode(
      ProgramMethod method, DefaultEnqueuerUseRegistry registry, EnqueuerWorklist worklist) {
    assert registry.getMaxApiReferenceLevel().isGreaterThanOrEqualTo(minApiLevel);
    if (appView.options().apiModelingOptions().tracedMethodApiLevelCallback != null) {
      appView
          .options()
          .apiModelingOptions()
          .tracedMethodApiLevelCallback
          .accept(method.getMethodReference(), registry.getMaxApiReferenceLevel());
    }
    computeAndSetApiLevelForDefinition(method);
    method.getDefinition().setApiLevelForCode(registry.getMaxApiReferenceLevel());
  }

  @Override
  public void processNewlyTargetedMethod(ProgramMethod method, EnqueuerWorklist worklist) {
    computeAndSetApiLevelForDefinition(method);
  }

  @Override
  public void processNewlyReachableField(ProgramField field, EnqueuerWorklist worklist) {
    computeAndSetApiLevelForDefinition(field);
  }

  @Override
  public void processNewlyLiveNonProgramType(ClasspathOrLibraryClass clazz) {
    clazz.forEachClassMethod(this::computeAndSetApiLevelForDefinition);
  }

  @Override
  public void processNewlyFailedMethodResolutionTarget(
      DexEncodedMethod method, EnqueuerWorklist worklist) {
    // We may not trace into failed resolution targets.
    method.setApiLevelForCode(ComputedApiLevel.unknown());
  }

  private void computeAndSetApiLevelForDefinition(DexClassAndMember<?, ?> member) {
    member
        .getDefinition()
        .setApiLevelForDefinition(
            apiCompute.computeApiLevelForDefinition(
                member.getReference(), appView.dexItemFactory(), ComputedApiLevel.unknown()));
  }
}
