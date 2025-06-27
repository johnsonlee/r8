// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;

public class NoDirectRuntimeTypeChecks extends SingleClassPolicy {

  private final HorizontalClassMergerOptions options;
  private final RuntimeTypeCheckInfo runtimeTypeCheckInfo;
  private final SyntheticItems syntheticItems;

  public NoDirectRuntimeTypeChecks(AppView<?> appView) {
    this(appView, null);
  }

  public NoDirectRuntimeTypeChecks(AppView<?> appView, RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    this.options = appView.options().horizontalClassMergerOptions();
    this.runtimeTypeCheckInfo = runtimeTypeCheckInfo;
    this.syntheticItems = appView.getSyntheticItems();
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    if (runtimeTypeCheckInfo == null) {
      assert syntheticItems.isSyntheticClass(clazz)
          : "Expected synthetic, got: " + clazz.getTypeName();
      return true;
    }
    if (clazz.isInterface() || !options.enableCheckCastRewriting) {
      // We cannot rewrite runtime type checks to classId comparisons, since we cannot add instance
      // fields to interfaces.
      return !runtimeTypeCheckInfo.isRuntimeCheckType(clazz);
    } else {
      // Allow check-casts to the base type, since we can use the classId field for correct
      // rewriting.
      return !runtimeTypeCheckInfo.isCheckCastArrayType(clazz)
          && !runtimeTypeCheckInfo.isExceptionGuardType(clazz)
          && !runtimeTypeCheckInfo.isInstanceOfType(clazz);
    }
  }

  @Override
  public String getName() {
    return "NoDirectRuntimeTypeChecks";
  }
}
