// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

public class TypeSwitchMethods {

  public static int typeSwitch(Object obj, int restart, Object[] tests) {
    if (obj == null) {
      return -1;
    }
    for (int i = restart; i < tests.length; i++) {
      Object test = tests[i];
      if (test instanceof Class<?>) {
        if (((Class<?>) test).isInstance(obj)) {
          return i;
        }
      } else {
        // This is an integer, a string or an enum instance.
        if (obj.equals(test)) {
          return i;
        }
      }
    }
    // Default case.
    return -2;
  }
}
