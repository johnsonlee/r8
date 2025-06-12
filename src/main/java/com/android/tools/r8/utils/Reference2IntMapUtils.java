// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;

public class Reference2IntMapUtils {

  public static <T> void increment(Reference2IntMap<T> map, T key) {
    increment(map, key, 1);
  }

  public static <T> void increment(Reference2IntMap<T> map, T key, int value) {
    map.put(key, map.getInt(key) + value);
  }
}
