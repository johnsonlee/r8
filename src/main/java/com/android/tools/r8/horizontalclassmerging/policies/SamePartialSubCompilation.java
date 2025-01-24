// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialD8SubCompilationConfiguration;

public class SamePartialSubCompilation
    extends MultiClassSameReferencePolicy<MethodConversionOptions.Target> {

  private final AppView<AppInfo> appView;
  private final R8PartialD8SubCompilationConfiguration subCompilationConfiguration;

  public SamePartialSubCompilation(AppView<AppInfo> appView) {
    assert appView.options().partialSubCompilationConfiguration != null;
    this.appView = appView;
    this.subCompilationConfiguration = appView.options().partialSubCompilationConfiguration.asD8();
  }

  @Override
  public MethodConversionOptions.Target getMergeKey(DexProgramClass clazz) {
    return subCompilationConfiguration.getTargetFor(clazz, appView);
  }

  @Override
  public String getName() {
    return "SamePartialSubCompilation";
  }
}
