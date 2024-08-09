// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import java.util.Iterator;
import java.util.function.Predicate;

public interface NextUntilIterator<T> extends Iterator<T> {

  /**
   * Continue to call {@link #next} while {@code predicate} tests {@code false}.
   *
   * @returns the item that matched the predicate or {@code null} if all items fail the predicate
   *     test
   */
  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  default <S extends T> S nextUntil(Predicate<T> predicate) {
    while (hasNext()) {
      T item = next();
      if (predicate.test(item)) {
        return (S) item;
      }
    }
    return null;
  }

  /**
   * Continue to call {@link #next} until it returns the given item.
   *
   * @returns item if it was found, null otherwise.
   */
  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  default <S extends T> S nextUntil(T item) {
    while (hasNext()) {
      if (next() == item) {
        return (S) item;
      }
    }
    return null;
  }
}
