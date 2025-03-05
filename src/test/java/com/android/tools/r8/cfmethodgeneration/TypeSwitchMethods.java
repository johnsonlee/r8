// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

public class TypeSwitchMethods {

  // By design this is lock-free so the JVM may compute several times the same value.
  public static boolean switchEnumEq(
      Object value, Object[] cache, int index, String enumClass, String name) {
    if (cache[index] == null) {
      Object resolved = null;
      try {
        Class<?> clazz = Class.forName(enumClass);
        if (clazz.isEnum()) {
          Class<? extends Enum> enumClazz = (Class<? extends Enum>) clazz;
          resolved = Enum.valueOf(enumClazz, name);
        }
      } catch (Throwable t) {
      }
      // R8 sets a sentinel if resolution has failed.
      cache[index] = resolved == null ? new Object() : resolved;
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
