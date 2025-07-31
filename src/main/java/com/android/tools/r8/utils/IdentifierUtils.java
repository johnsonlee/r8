// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public class IdentifierUtils {
  public static boolean isDexIdentifierStart(int cp) {
    // Dex does not have special restrictions on the first char of an identifier.
    return isDexIdentifierPart(cp);
  }

  public static boolean isDexIdentifierPart(int cp) {
    return isSimpleNameChar(cp);
  }

  public static boolean isRelaxedDexIdentifierPart(int cp) {
    return isSimpleNameChar(cp)
      || isUnicodeSpace(cp);
  }

  public static boolean isQuestionMark(int cp) {
    return cp == '?';
  }

  public static boolean isUnicodeSpace(int cp) {
    return com.android.tools.r8.keepanno.utils.IdentifierUtils.isUnicodeSpace(cp);
  }

  private static boolean isSimpleNameChar(int cp) {
    return com.android.tools.r8.keepanno.utils.IdentifierUtils.isSimpleNameChar(cp);
  }
}
