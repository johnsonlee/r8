// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public class KeepMethodNamePattern {

  private static final String INIT_STRING = "<init>";
  private static final String CLINIT_STRING = "<clinit>";
  private static final KeepMethodNamePattern ANY =
      new KeepMethodNamePattern(KeepStringPattern.any());
  private static final KeepMethodNamePattern INSTANCE_INIT =
      new KeepMethodNamePattern(KeepStringPattern.exact(INIT_STRING));
  private static final KeepMethodNamePattern CLASS_INIT =
      new KeepMethodNamePattern(KeepStringPattern.exact(CLINIT_STRING));

  public static KeepMethodNamePattern any() {
    return KeepMethodNamePattern.ANY;
  }

  public static KeepMethodNamePattern instanceInitializer() {
    return KeepMethodNamePattern.INSTANCE_INIT;
  }

  public static KeepMethodNamePattern classInitializer() {
    return KeepMethodNamePattern.CLASS_INIT;
  }

  public static KeepMethodNamePattern exact(String methodName) {
    return fromStringPattern(KeepStringPattern.exact(methodName));
  }

  public static KeepMethodNamePattern fromStringPattern(KeepStringPattern pattern) {
    if (pattern.isAny()) {
      return KeepMethodNamePattern.ANY;
    }
    if (pattern.isExact()) {
      String exact = pattern.asExactString();
      if (INIT_STRING.equals(exact)) {
        return KeepMethodNamePattern.INSTANCE_INIT;
      }
      if (CLINIT_STRING.equals(exact)) {
        return KeepMethodNamePattern.CLASS_INIT;
      }
    }
    return new KeepMethodNamePattern(pattern);
  }

  private final KeepStringPattern pattern;

  private KeepMethodNamePattern(KeepStringPattern pattern) {
    assert pattern != null;
    this.pattern = pattern;
  }

  public KeepStringPattern asStringPattern() {
    return pattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepMethodNamePattern)) {
      return false;
    }
    KeepMethodNamePattern that = (KeepMethodNamePattern) o;
    return pattern.equals(that.pattern);
  }

  @Override
  public int hashCode() {
    return pattern.hashCode();
  }

  @Override
  public String toString() {
    return pattern.toString();
  }

  public boolean isAny() {
    return ANY == this;
  }

  public boolean isClassInitializer() {
    return CLASS_INIT == this;
  }

  public boolean isInstanceInitializer() {
    return INSTANCE_INIT == this;
  }

  public boolean isExact() {
    return pattern.isExact();
  }

  public String asExactString() {
    return pattern.asExactString();
  }
}
