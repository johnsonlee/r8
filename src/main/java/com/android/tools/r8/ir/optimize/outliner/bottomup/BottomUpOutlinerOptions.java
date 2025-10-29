// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.bottomup;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.utils.SystemPropertyUtils;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class BottomUpOutlinerOptions {

  public boolean enable =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.outliner.enable", false);

  public boolean enableStringBuilderOutlining =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.outliner.enablestringbuilder", false);

  public final int costInBytesForTesting =
      SystemPropertyUtils.parseSystemPropertyOrDefault("com.android.tools.r8.outliner.cost", -1);

  public boolean forceDebug =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.outliner.forcedebug", false);

  public final int forceUsers =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.outliner.users", Integer.MAX_VALUE);

  public boolean neverCompile =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.outliner.nevercompile", false);

  public Consumer<Collection<Outline>> outlineConsumerForTesting = null;
  public Predicate<Outline> outlineStrategyForTesting = null;

  public boolean isEnabled(AppView<?> appView) {
    if (!appView.options().isGeneratingDex() || !enable) {
      return false;
    }
    if (appView.options().debug && !forceDebug) {
      return false;
    }
    if (appView.options().partialSubCompilationConfiguration != null) {
      return false;
    }
    return true;
  }
}
