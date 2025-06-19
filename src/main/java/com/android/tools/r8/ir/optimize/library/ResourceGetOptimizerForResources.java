// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;

public class ResourceGetOptimizerForResources extends ResourceGetOptimizer {
  ResourceGetOptimizerForResources(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
  }

  @Override
  public DexType getType() {
    return dexItemFactory.androidResourcesType;
  }
}
