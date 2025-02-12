// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.google.common.collect.Iterables;

public class CallOtherMergeableSyntheticMethodUseRegistry
    extends DefaultUseRegistryWithResult<Boolean, ProgramMethod> {

  public CallOtherMergeableSyntheticMethodUseRegistry(AppView<?> appView, ProgramMethod context) {
    super(appView, context, false);
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    if (appView.getSyntheticItems().isSynthetic(method.getHolderType())
        && isSyntheticMethodAllowingGlobalMerging(method)) {
      setResult(true);
    }
  }

  private boolean isSyntheticMethodAllowingGlobalMerging(DexMethod method) {
    return Iterables.all(
        appView.getSyntheticItems().getSyntheticKinds(method.getHolderType()),
        kind ->
            kind.isSyntheticMethodKind() && kind.asSyntheticMethodKind().isAllowGlobalMerging());
  }
}
