// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.utils.SystemPropertyUtils;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ThrowBlockOutlinerOptions {

  public boolean enable =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.throwblockoutliner.enable", false);

  public final int costInBytesForTesting =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.throwblockoutliner.cost", -1);

  public boolean forceDebug =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.throwblockoutliner.forcedebug", false);

  public final int forceUsers =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.throwblockoutliner.users", Integer.MAX_VALUE);

  public Consumer<Collection<ThrowBlockOutline>> outlineConsumerForTesting = null;
  public Predicate<ThrowBlockOutline> outlineStrategyForTesting = null;

  public boolean isEnabled(AppView<?> appView) {
    if (!appView.options().isGeneratingDex() || !enable) {
      return false;
    }
    if (appView.options().debug && !forceDebug) {
      return false;
    }
    return true;
  }
}
