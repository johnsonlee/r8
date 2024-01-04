// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public class KeepFieldNamePattern {

  private static final KeepFieldNamePattern ANY = new KeepFieldNamePattern(KeepStringPattern.any());

  public static KeepFieldNamePattern any() {
    return ANY;
  }

  public static KeepFieldNamePattern exact(String methodName) {
    return fromStringPattern(KeepStringPattern.exact(methodName));
  }

  public static KeepFieldNamePattern fromStringPattern(KeepStringPattern pattern) {
    if (pattern.isAny()) {
      return ANY;
    }
    return new KeepFieldNamePattern(pattern);
  }

  private final KeepStringPattern pattern;

  private KeepFieldNamePattern(KeepStringPattern pattern) {
    this.pattern = pattern;
  }

  public KeepStringPattern asStringPattern() {
    return pattern;
  }

  public boolean isAny() {
    return ANY == this;
  }

  public final boolean isExact() {
    return pattern.isExact();
  }

  public String asExactString() {
    return pattern.asExactString();
  }
}
