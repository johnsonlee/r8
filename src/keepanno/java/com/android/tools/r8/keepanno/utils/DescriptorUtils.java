// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.utils;

public class DescriptorUtils {
  public static boolean isValidClassDescriptor(String string) {
    if (string.length() < 3
        || string.charAt(0) != 'L'
        || string.charAt(string.length() - 1) != ';') {
      return false;
    }
    if (string.charAt(1) == '/' || string.charAt(string.length() - 2) == '/') {
      return false;
    }
    int cp;
    for (int i = 1; i < string.length() - 1; i += Character.charCount(cp)) {
      cp = string.codePointAt(i);
      if (cp != '/' && !IdentifierUtils.isRelaxedDexIdentifierPart(cp)) {
        return false;
      }
    }
    return true;
  }
}
