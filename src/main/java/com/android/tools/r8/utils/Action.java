// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.concurrent.Callable;

@FunctionalInterface
public interface Action extends Callable<Void> {

  Action EMPTY = () -> {};

  static Action empty() {
    return EMPTY;
  }

  @Override
  default Void call() throws Exception {
    execute();
    return null;
  }

  void execute();
}
