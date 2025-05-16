// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import java.util.function.Consumer;
import java.util.function.Function;

public class R8PartialUtils {

  public static void acceptR8PartialR8SubCompilationConfiguration(
      AppView<?> appView, Consumer<R8PartialR8SubCompilationConfiguration> fn) {
    R8PartialSubCompilationConfiguration subCompilationConfiguration =
        appView.options().partialSubCompilationConfiguration;
    if (subCompilationConfiguration != null && subCompilationConfiguration.isR8()) {
      fn.accept(subCompilationConfiguration.asR8());
    }
  }

  public static <T> T applyR8PartialR8SubCompilationConfiguration(
      AppView<?> appView, Function<R8PartialR8SubCompilationConfiguration, T> fn, T defaultValue) {
    R8PartialSubCompilationConfiguration subCompilationConfiguration =
        appView.options().partialSubCompilationConfiguration;
    if (subCompilationConfiguration != null && subCompilationConfiguration.isR8()) {
      return fn.apply(subCompilationConfiguration.asR8());
    }
    return defaultValue;
  }
}
