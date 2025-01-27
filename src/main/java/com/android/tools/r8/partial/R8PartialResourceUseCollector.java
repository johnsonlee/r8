// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.ResourceShrinker;
import com.android.tools.r8.ResourceShrinker.ReferenceChecker;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.utils.DescriptorUtils;

public abstract class R8PartialResourceUseCollector implements ReferenceChecker {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public R8PartialResourceUseCollector(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  public void run() {
    R8PartialR8SubCompilationConfiguration r8SubCompilationConfiguration =
        appView.options().partialSubCompilationConfiguration.asR8();
    ResourceShrinker.runForTesting(r8SubCompilationConfiguration.getDexingOutputClasses(), this);
  }

  protected abstract void keep(int resourceId);

  @Override
  public boolean shouldProcess(String internalName) {
    // Only process R classes.
    return DescriptorUtils.isRClassDescriptor(
        DescriptorUtils.internalNameToDescriptor(internalName));
  }

  @Override
  public void referencedInt(int value) {
    keep(value);
  }

  @Override
  public void referencedString(String value) {}

  @Override
  public void referencedStaticField(String internalName, String fieldName) {}

  @Override
  public void referencedMethod(String internalName, String methodName, String methodDescriptor) {}
}
