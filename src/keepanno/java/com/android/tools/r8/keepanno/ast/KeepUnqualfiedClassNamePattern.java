// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public class KeepUnqualfiedClassNamePattern {

  private static final KeepUnqualfiedClassNamePattern ANY =
      new KeepUnqualfiedClassNamePattern(KeepStringPattern.any());

  public static KeepUnqualfiedClassNamePattern any() {
    return ANY;
  }

  public static KeepUnqualfiedClassNamePattern exact(String className) {
    return new KeepUnqualfiedClassNamePattern(KeepStringPattern.exact(className));
  }

  public static KeepUnqualfiedClassNamePattern fromStringPattern(KeepStringPattern pattern) {
    return builder().setPattern(pattern).build();
  }

  public static Builder builder() {
    return new Builder();
  }

  private final KeepStringPattern simpleNamePattern;

  private KeepUnqualfiedClassNamePattern(KeepStringPattern simpleNamePattern) {
    this.simpleNamePattern = simpleNamePattern;
  }

  public boolean isAny() {
    return simpleNamePattern.isAny();
  }

  public boolean isExact() {
    return simpleNamePattern.isExact();
  }

  public String asExactString() {
    return simpleNamePattern.asExactString();
  }

  public KeepStringPattern asStringPattern() {
    return simpleNamePattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepUnqualfiedClassNamePattern)) {
      return false;
    }
    KeepUnqualfiedClassNamePattern that = (KeepUnqualfiedClassNamePattern) o;
    return simpleNamePattern.equals(that.simpleNamePattern);
  }

  @Override
  public int hashCode() {
    return simpleNamePattern.hashCode();
  }

  @Override
  public String toString() {
    return simpleNamePattern.toString();
  }

  public static class Builder {

    private KeepStringPattern pattern = KeepStringPattern.any();

    public Builder any() {
      pattern = KeepStringPattern.any();
      return this;
    }

    public Builder exact(String className) {
      pattern = KeepStringPattern.exact(className);
      return this;
    }

    public Builder setPattern(KeepStringPattern pattern) {
      this.pattern = pattern;
      return this;
    }

    public KeepUnqualfiedClassNamePattern build() {
      if (pattern == null) {
        throw new KeepEdgeException("Invalid class name pattern: null");
      }
      if (pattern.isAny()) {
        return KeepUnqualfiedClassNamePattern.any();
      }
      return new KeepUnqualfiedClassNamePattern(pattern);
    }
  }
}
