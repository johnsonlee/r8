// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.TraversalContinuation;

/**
 * When the app contains a method handle such as T::new, then we need to treat T.<init> as pinned.
 * If we merge T with another class, we could end up changing the signature of T.<init>, which
 * breaks the T::new method handle.
 *
 * <p>During tree shaking, in {@link com.android.tools.r8.shaking.Enqueuer#traceCallSite}, we
 * disable closed world reasoning for methods that are referenced from a lambda method handle.
 *
 * <p>We fix this issue when compiling to class files by disallowing class merging of classes that
 * have a method where closed world reasoning is disallowed.
 */
public class NoMethodHandleFromLambda extends SingleClassPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoMethodHandleFromLambda(AppView<AppInfoWithLiveness> appView) {
    assert appView.options().isGeneratingClassFiles();
    this.appView = appView;
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    return clazz
        .traverseProgramMethods(
            method -> {
              KeepMethodInfo keepInfo = appView.getKeepInfo(method);
              return TraversalContinuation.continueIf(
                  keepInfo.isClosedWorldReasoningAllowed(appView.options()));
            })
        .shouldContinue();
  }

  @Override
  public String getName() {
    return "NoConstructorMethodHandleFromLambda";
  }
}
