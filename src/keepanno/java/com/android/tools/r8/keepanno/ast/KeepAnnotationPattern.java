// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

public class KeepAnnotationPattern {

  private static final int RUNTIME_RETENTION_MASK = 0x1;
  private static final int CLASS_RETENTION_MASK = 0x2;
  private static final int ANY_RETENTION_MASK = 0x3;

  private static final KeepAnnotationPattern ANY_WITH_ANY_RETENTION =
      new KeepAnnotationPattern(KeepQualifiedClassNamePattern.any(), ANY_RETENTION_MASK);

  private static final KeepAnnotationPattern ANY_WITH_RUNTIME_RETENTION =
      new KeepAnnotationPattern(KeepQualifiedClassNamePattern.any(), RUNTIME_RETENTION_MASK);

  private static final KeepAnnotationPattern ANY_WITH_CLASS_RETENTION =
      new KeepAnnotationPattern(KeepQualifiedClassNamePattern.any(), CLASS_RETENTION_MASK);

  public static KeepAnnotationPattern any() {
    return KeepAnnotationPattern.ANY_WITH_ANY_RETENTION;
  }

  public static KeepAnnotationPattern anyWithRuntimeRetention() {
    return KeepAnnotationPattern.ANY_WITH_RUNTIME_RETENTION;
  }

  public static KeepAnnotationPattern anyWithClassRetention() {
    return KeepAnnotationPattern.ANY_WITH_CLASS_RETENTION;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private KeepQualifiedClassNamePattern namePattern = KeepQualifiedClassNamePattern.any();
    private int retentionPolicies = 0x0;

    private Builder() {}

    public Builder setNamePattern(KeepQualifiedClassNamePattern namePattern) {
      this.namePattern = namePattern;
      return this;
    }

    public Builder addRetentionPolicy(RetentionPolicy policy) {
      switch (policy) {
        case RUNTIME:
          retentionPolicies |= RUNTIME_RETENTION_MASK;
          break;
        case CLASS:
          retentionPolicies |= CLASS_RETENTION_MASK;
          break;
        case SOURCE:
          throw new KeepEdgeException("Retention policy SOURCE cannot be used in patterns");
        default:
          throw new KeepEdgeException("Invalid policy: " + policy);
      }
      return this;
    }

    public KeepAnnotationPattern build() {
      if (retentionPolicies == 0x0) {
        throw new KeepEdgeException("Invalid empty retention policy");
      }
      if (namePattern.isAny()) {
        switch (retentionPolicies) {
          case RUNTIME_RETENTION_MASK:
            return ANY_WITH_RUNTIME_RETENTION;
          case CLASS_RETENTION_MASK:
            return ANY_WITH_CLASS_RETENTION;
          case ANY_RETENTION_MASK:
            return ANY_WITH_ANY_RETENTION;
          default:
            throw new KeepEdgeException("Invalid retention policy value: " + retentionPolicies);
        }
      }
      return new KeepAnnotationPattern(namePattern, retentionPolicies);
    }
  }

  private final KeepQualifiedClassNamePattern namePattern;
  private final int retentionPolicies;

  private KeepAnnotationPattern(KeepQualifiedClassNamePattern namePattern, int retentionPolicies) {
    assert namePattern != null;
    this.namePattern = namePattern;
    this.retentionPolicies = retentionPolicies;
  }

  public boolean isAny() {
    return this == ANY_WITH_ANY_RETENTION;
  }

  public boolean isAnyWithRuntimeRetention() {
    return this == ANY_WITH_RUNTIME_RETENTION;
  }

  public boolean isAnyWithClassRetention() {
    return this == ANY_WITH_CLASS_RETENTION;
  }

  public KeepQualifiedClassNamePattern getNamePattern() {
    return namePattern;
  }

  public boolean includesRuntimeRetention() {
    return (retentionPolicies & RUNTIME_RETENTION_MASK) > 0;
  }

  public boolean includesClassRetention() {
    return (retentionPolicies & CLASS_RETENTION_MASK) > 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepAnnotationPattern)) {
      return false;
    }
    KeepAnnotationPattern that = (KeepAnnotationPattern) o;
    return retentionPolicies == that.retentionPolicies && namePattern.equals(that.namePattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namePattern, retentionPolicies);
  }
}
