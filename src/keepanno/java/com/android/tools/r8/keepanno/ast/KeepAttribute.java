// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

public enum KeepAttribute {
  RUNTIME_VISIBLE_ANNOTATIONS("RuntimeVisibleAnnotations"),
  RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS("RuntimeVisibleParameterAnnotations"),
  RUNTIME_VISIBLE_TYPE_ANNOTATIONS("RuntimeVisibleTypeAnnotations"),
  RUNTIME_INVISIBLE_ANNOTATIONS("RuntimeInvisibleAnnotations"),
  RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS("RuntimeInvisibleParameterAnnotations"),
  RUNTIME_INVISIBLE_TYPE_ANNOTATIONS("RuntimeInvisibleTypeAnnotations");

  private final String printName;

  KeepAttribute(String printName) {
    this.printName = printName;
  }

  public String getPrintName() {
    return printName;
  }
}
