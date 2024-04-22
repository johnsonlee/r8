// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext.PropertyParsingContext;
import java.util.function.Function;

/**
 * Utilities for mapping the syntax used in annotations to the keep-edge AST.
 *
 * <p>The AST explicitly avoids interpreting type strings as they are potentially ambiguous. These
 * utilities define the mappings from such syntax strings into the AST.
 */
public class KeepEdgeReaderUtils {

  public static String getBinaryNameFromClassTypeName(String classTypeName) {
    return classTypeName.replace('.', '/');
  }

  public static String getDescriptorFromClassTypeName(String classTypeName) {
    return getDescriptorFromBinaryName(getBinaryNameFromClassTypeName(classTypeName));
  }

  public static String getDescriptorFromBinaryName(String binaryName) {
    return "L" + binaryName + ";";
  }

  public static String getJavaTypeFromDescriptor(String descriptor) {
    if (descriptor.length() == 1) {
      switch (descriptor.charAt(0)) {
        case 'Z':
          return "boolean";
        case 'B':
          return "byte";
        case 'C':
          return "char";
        case 'S':
          return "short";
        case 'I':
          return "int";
        case 'J':
          return "long";
        case 'F':
          return "float";
        case 'D':
          return "double";
        case 'V':
          return "void";
        default:
          throw new IllegalStateException("Unexpected descriptor: " + descriptor);
      }
    }
    if (descriptor.charAt(0) == '[') {
      return getJavaTypeFromDescriptor(descriptor.substring(1)) + "[]";
    }
    if (descriptor.charAt(0) == 'L') {
      return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }
    throw new IllegalStateException("Unexpected descriptor: " + descriptor);
  }

  public static KeepTypePattern typePatternFromString(
      String string, PropertyParsingContext property) {
    return KeepTypePattern.fromDescriptor(internalDescriptorFromJavaType(string, property::error));
  }

  public static String getDescriptorFromJavaType(String type) {
    return internalDescriptorFromJavaType(type, IllegalStateException::new);
  }

  private static String internalDescriptorFromJavaType(
      String type, Function<String, RuntimeException> onError) {
    switch (type) {
      case "boolean":
        return "Z";
      case "byte":
        return "B";
      case "char":
        return "C";
      case "short":
        return "S";
      case "int":
        return "I";
      case "long":
        return "J";
      case "float":
        return "F";
      case "double":
        return "D";
      case "void":
        return "V";
      default:
        {
          StringBuilder builder = new StringBuilder(type.length());
          int i = type.length() - 1;
          if (i < 0) {
            throw onError.apply("Invalid empty type");
          }
          while (type.charAt(i) == ']') {
            if (type.charAt(--i) != '[') {
              throw onError.apply("Invalid type: '" + type + "'");
            }
            builder.append('[');
            --i;
          }
          builder.append('L');
          for (int j = 0; j <= i; j++) {
            char c = type.charAt(j);
            builder.append(c == '.' ? '/' : c);
          }
          builder.append(';');
          return builder.toString();
        }
    }
  }
}
