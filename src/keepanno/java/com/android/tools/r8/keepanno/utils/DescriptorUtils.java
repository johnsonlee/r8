// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.utils;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

// TODO(b/444623706): Avoid the duplication and multiple DescriptorUtils classes by making a shared
// library for R8 and keepanno.
public class DescriptorUtils {
  public static final char DESCRIPTOR_PACKAGE_SEPARATOR = '/';
  public static final char JAVA_PACKAGE_SEPARATOR = '.';

  private static final Map<String, String> typeNameToLetterMap =
      ImmutableMap.<String, String>builder()
          .put("void", "V")
          .put("boolean", "Z")
          .put("byte", "B")
          .put("short", "S")
          .put("char", "C")
          .put("int", "I")
          .put("long", "J")
          .put("float", "F")
          .put("double", "D")
          .build();

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

  private static String internalToDescriptor(
      String typeName, boolean shorty, boolean ignorePrimitives) {
    String descriptor = null;
    if (!ignorePrimitives) {
      descriptor = typeNameToLetterMap.get(typeName);
    }
    if (descriptor != null) {
      return descriptor;
    }
    // Must be some array or object type.
    if (shorty) {
      return "L";
    }
    if (typeName.endsWith("[]")) {
      return "["
          + internalToDescriptor(
              typeName.substring(0, typeName.length() - 2), shorty, ignorePrimitives);
    }
    // Must be an object type.
    return "L" + typeName.replace(JAVA_PACKAGE_SEPARATOR, DESCRIPTOR_PACKAGE_SEPARATOR) + ";";
  }

  /**
   * Convert a Java type name to a descriptor string.
   *
   * @param typeName the java type name
   * @return the descriptor string
   */
  public static String javaTypeToDescriptor(String typeName) {
    assert typeName.indexOf(DESCRIPTOR_PACKAGE_SEPARATOR) == -1;
    return internalToDescriptor(typeName, false, false);
  }
}
