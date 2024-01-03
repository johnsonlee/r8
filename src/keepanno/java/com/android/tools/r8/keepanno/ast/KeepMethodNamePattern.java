// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;


public abstract class KeepMethodNamePattern {
  private static final String INIT_STRING = "<init>";
  private static final String CLINIT_STRING = "<clinit>";

  public static KeepMethodNamePattern any() {
    return SomePattern.ANY;
  }

  public static KeepMethodNamePattern initializer() {
    return SomePattern.INSTANCE_INIT;
  }

  public static KeepMethodNamePattern exact(String methodName) {
    return fromStringPattern(KeepStringPattern.exact(methodName));
  }

  public static KeepMethodNamePattern fromStringPattern(KeepStringPattern pattern) {
    if (pattern.isAny()) {
      return SomePattern.ANY;
    }
    if (pattern.isExact()) {
      String exact = pattern.asExactString();
      if (INIT_STRING.equals(exact)) {
        return SomePattern.INSTANCE_INIT;
      }
      if (CLINIT_STRING.equals(exact)) {
        return SomePattern.CLASS_INIT;
      }
    }
    return new SomePattern(pattern);
  }

  private KeepMethodNamePattern() {}

  public abstract boolean isAny();

  public abstract boolean isInstanceInitializer();

  public abstract boolean isClassInitializer();

  public abstract boolean isExact();

  public abstract String asExactString();

  public abstract KeepStringPattern asStringPattern();

  private static class SomePattern extends KeepMethodNamePattern {
    private static final SomePattern ANY = new SomePattern(KeepStringPattern.any());
    private static final KeepMethodNamePattern INSTANCE_INIT =
        new SomePattern(KeepStringPattern.exact("<init>"));
    private static final KeepMethodNamePattern CLASS_INIT =
        new SomePattern(KeepStringPattern.exact("<clinit>"));

    private final KeepStringPattern pattern;

    public SomePattern(KeepStringPattern pattern) {
      assert pattern != null;
      this.pattern = pattern;
    }

    @Override
    public KeepStringPattern asStringPattern() {
      return pattern;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SomePattern)) {
        return false;
      }
      SomePattern that = (SomePattern) o;
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

    @Override
    public boolean isAny() {
      return ANY == this;
    }

    @Override
    public boolean isClassInitializer() {
      return CLASS_INIT == this;
    }

    @Override
    public boolean isInstanceInitializer() {
      return INSTANCE_INIT == this;
    }

    @Override
    public boolean isExact() {
      return pattern.isExact();
    }

    @Override
    public String asExactString() {
      return pattern.asExactString();
    }
  }
}
