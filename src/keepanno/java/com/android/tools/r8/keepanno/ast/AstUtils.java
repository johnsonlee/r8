// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AstUtils {
  private AstUtils() {}

  public static <T> Function<T, Void> toVoidFunction(Consumer<T> fn) {
    return t -> {
      fn.accept(t);
      return null;
    };
  }

  public static Supplier<Void> toVoidSupplier(Runnable fn) {
    return () -> {
      fn.run();
      return null;
    };
  }
}
