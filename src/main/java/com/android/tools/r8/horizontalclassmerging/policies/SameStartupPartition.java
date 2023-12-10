// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.SameStartupPartition.StartupPartition;
import com.android.tools.r8.profile.startup.StartupOptions;
import com.android.tools.r8.profile.startup.profile.StartupProfile;

public class SameStartupPartition extends MultiClassSameReferencePolicy<StartupPartition> {

  public enum StartupPartition {
    STARTUP,
    POST_STARTUP
  }

  private final StartupOptions startupOptions;
  private final StartupProfile startupProfile;

  public SameStartupPartition(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.startupOptions = appView.options().getStartupOptions();
    this.startupProfile = appView.getStartupProfile();
  }

  @Override
  public StartupPartition getMergeKey(DexProgramClass clazz) {
    return startupProfile.isStartupClass(clazz.getType())
        ? StartupPartition.STARTUP
        : StartupPartition.POST_STARTUP;
  }

  @Override
  public boolean shouldSkipPolicy() {
    return startupProfile.isEmpty() || startupOptions.isStartupBoundaryOptimizationsEnabled();
  }

  @Override
  public String getName() {
    return "SameStartupPartition";
  }
}
