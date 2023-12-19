// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class NoAbstractMethodsOnAbstractClassesPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NoAbstractMethodsOnAbstractClassesPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    if (!group.getSource().isAbstract() || group.getTarget().isAbstract()) {
      return true;
    }
    for (ProgramMethod method :
        group.getSource().virtualProgramMethods(DexEncodedMethod::isAbstract)) {
      DexClassAndMethod resolvedMethod =
          appView
              .appInfo()
              .resolveMethodOn(group.getTarget(), method.getReference())
              .getResolutionPair();
      // If the method and resolved method are different, then the abstract method will be removed
      // and references will be rewritten to the resolved method.
      if (resolvedMethod.getDefinition() == method.getDefinition()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String getName() {
    return "NoAbstractMethodsOnAbstractClassesPolicy";
  }
}
