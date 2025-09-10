// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AwaitableFutures {

  public static void awaitAll(Awaitable... awaitables) {
    List<Throwable> exceptions = null;
    for (Awaitable awaitable : awaitables) {
      try {
        awaitable.await();
      } catch (Throwable t) {
        if (exceptions == null) {
          exceptions = new ArrayList<>();
        }
        exceptions.add(t);
      }
    }
    if (exceptions != null) {
      if (exceptions.size() == 1) {
        throw new RuntimeException(
            "Exceptions waiting for futures: " + exceptions.get(0).getMessage(), exceptions.get(0));
      } else {
        RuntimeException e =
            new RuntimeException(
                "Multiple ("
                    + exceptions.size()
                    + ") exceptions waiting for futures: "
                    + exceptions.stream()
                        .map(Throwable::getMessage)
                        .collect(Collectors.joining(", ")));
        exceptions.forEach(e::addSuppressed);
        throw e;
      }
    }
  }
}
