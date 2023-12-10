// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.profile.startup.optimization;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.startup.profile.StartupProfile;

public class StartupBoundaryOptimizationUtils {

  public static boolean isSafeForInlining(
      ProgramMethod caller,
      ProgramMethod callee,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    StartupProfile startupProfile = appView.getStartupProfile();
    if (startupProfile.isEmpty()
        || appView.options().getStartupOptions().isStartupBoundaryOptimizationsEnabled()
        || callee.getOptimizationInfo().forceInline()) {
      return true;
    }
    // It is always OK to inline into a non-startup class.
    if (!startupProfile.isStartupClass(caller.getHolderType())) {
      return true;
    }
    // Otherwise the caller is a startup method or a post-startup method on a non-startup class. In
    // either case, only allow inlining if the callee is defined on a startup class.
    return startupProfile.isStartupClass(callee.getHolderType());
  }

  public static boolean isSafeForVerticalClassMerging(
      DexProgramClass sourceClass,
      DexProgramClass targetClass,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    StartupProfile startupProfile = appView.getStartupProfile();
    if (startupProfile.isEmpty()
        || appView.options().getStartupOptions().isStartupBoundaryOptimizationsEnabled()) {
      return true;
    }
    return !startupProfile.isStartupClass(sourceClass.getType())
        || startupProfile.isStartupClass(targetClass.getType());
  }
}
