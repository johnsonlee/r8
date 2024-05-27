// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.api.genericsignature;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.function.Function;

// Default KeepForApi will retain everything, including generic signatures.
@KeepForApi
public class MyValueBox<S> {

  private final S value;

  public MyValueBox(S value) {
    this.value = value;
  }

  public <T> T run(Function<S, T> fn) {
    return fn.apply(value);
  }
}
