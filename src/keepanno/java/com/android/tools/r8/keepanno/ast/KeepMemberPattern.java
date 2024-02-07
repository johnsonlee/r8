// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Objects;

public abstract class KeepMemberPattern {

  public static KeepMemberPattern allMembers() {
    return Some.ANY;
  }

  public static Builder memberBuilder() {
    return new Builder();
  }

  public static class Builder {
    private OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern;
    private KeepMemberAccessPattern accessPattern = KeepMemberAccessPattern.anyMemberAccess();

    public Builder setAnnotatedByPattern(
        OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern) {
      this.annotatedByPattern = annotatedByPattern;
      return this;
    }

    public Builder setAccessPattern(KeepMemberAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
      return this;
    }

    public KeepMemberPattern build() {
      if (annotatedByPattern.isAbsent() && accessPattern.isAny()) {
        return allMembers();
      }
      return new Some(annotatedByPattern, accessPattern);
    }
  }

  private static class Some extends KeepMemberPattern {
    private static final KeepMemberPattern ANY =
        new Some(OptionalPattern.absent(), KeepMemberAccessPattern.anyMemberAccess());

    private final OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern;
    private final KeepMemberAccessPattern accessPattern;

    public Some(
        OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern,
        KeepMemberAccessPattern accessPattern) {
      this.annotatedByPattern = annotatedByPattern;
      this.accessPattern = accessPattern;
    }

    @Override
    public OptionalPattern<KeepQualifiedClassNamePattern> getAnnotatedByPattern() {
      return annotatedByPattern;
    }

    @Override
    public KeepMemberAccessPattern getAccessPattern() {
      return accessPattern;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Some)) {
        return false;
      }
      Some some = (Some) o;
      return annotatedByPattern.equals(some.annotatedByPattern)
          && accessPattern.equals(some.accessPattern);
    }

    @Override
    public int hashCode() {
      return Objects.hash(annotatedByPattern, accessPattern);
    }

    @Override
    public String toString() {
      return "Member{"
          + annotatedByPattern.mapOrDefault(p -> "@" + p + ", ", "")
          + "access="
          + accessPattern
          + '}';
    }
  }

  KeepMemberPattern() {}

  public boolean isAllMembers() {
    return this == Some.ANY;
  }

  public final boolean isGeneralMember() {
    return !isMethod() && !isField();
  }

  public final boolean isMethod() {
    return asMethod() != null;
  }

  public KeepMethodPattern asMethod() {
    return null;
  }

  public final boolean isField() {
    return asField() != null;
  }

  public KeepFieldPattern asField() {
    return null;
  }

  public abstract KeepMemberAccessPattern getAccessPattern();

  public abstract OptionalPattern<KeepQualifiedClassNamePattern> getAnnotatedByPattern();
}
