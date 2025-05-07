// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

public class TypeSwitchMethods {

  private enum Enum {
    A;
  }

  // By design this is lock-free so the JVM may compute several times the same value.
  public static boolean switchEnumEq(Object value, Object[] cache, int index, String name) {
    if (cache[index] == null) {
      Object resolved = null;
      try {
        // Enum.class should be replaced when used by the correct enum class.
        resolved = Enum.valueOf(name);
      } catch (Throwable t) {
      }
      // R8 sets a sentinel if resolution has failed.
      cache[index] = resolved == null ? new Object() : resolved;
    }
    return value == cache[index];
  }

  // Specialized version for a single enum to avoid unboxing.
  public static boolean switchSpecializedEnumEq(
      Object value, Enum[] cache, boolean[] cacheValueSet, int index, String name) {
    if (!cacheValueSet[index]) {
      try {
        // Enum.class will be replaced when used by the correct enum class.
        cache[index] = Enum.valueOf(name);
      } catch (Throwable t) {
        // If this is hit, null is left in the cache.
      }
      cacheValueSet[index] = true;
    }
    if (cache == null) {
      // The cache is null if the enum class is missing.
      return false;
    }
    return value == cache[index];
  }

  public static boolean switchIntEq(Object value, int constant) {
    if (value instanceof Number) {
      Number num = (Number) value;
      return constant == num.intValue();
    }
    if (value instanceof Character) {
      Character ch = (Character) value;
      return constant == ch.charValue();
    }
    return false;
  }
}
