// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public abstract class KeepMemberPattern {

  public static KeepMemberPattern allMembers() {
    return Some.ANY;
  }

  public static Builder memberBuilder() {
    return new Builder();
  }

  public static class Builder {
    private KeepMemberAccessPattern accessPattern = KeepMemberAccessPattern.anyMemberAccess();

    public Builder setAccessPattern(KeepMemberAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
      return this;
    }

    public KeepMemberPattern build() {
      if (accessPattern.isAny()) {
        return allMembers();
      }
      return new Some(accessPattern);
    }
  }

  private static class Some extends KeepMemberPattern {
    private static final KeepMemberPattern ANY =
        new Some(KeepMemberAccessPattern.anyMemberAccess());

    private final KeepMemberAccessPattern accessPattern;

    public Some(KeepMemberAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
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
      return accessPattern.equals(some.accessPattern);
    }

    @Override
    public int hashCode() {
      return accessPattern.hashCode();
    }

    @Override
    public String toString() {
      return "Member{" + "access=" + accessPattern + '}';
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
}
