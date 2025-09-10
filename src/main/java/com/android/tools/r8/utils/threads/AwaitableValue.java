// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.threads;

import java.util.concurrent.ExecutionException;

public interface AwaitableValue<T> extends Awaitable {

  T awaitValue() throws ExecutionException;

  @Override
  default void await() throws ExecutionException {
    awaitValue();
  }
}
