// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.ClassItemPattern;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class KeepClassItemPattern extends KeepItemPattern {

  public static KeepClassItemPattern any() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static KeepClassItemPattern fromClassProto(ClassItemPattern proto) {
    return builder().applyProto(proto).build();
  }

  public ClassItemPattern.Builder buildClassProto() {
    // TODO(b/343389186): Add instance-of.
    // TODO(b/343389186): Add annotated-by.
    return ClassItemPattern.newBuilder().setClassName(classNamePattern.buildProto());
  }

  public static class Builder {

    private KeepQualifiedClassNamePattern classNamePattern = KeepQualifiedClassNamePattern.any();
    private KeepInstanceOfPattern instanceOfPattern = KeepInstanceOfPattern.any();
    private OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern =
        OptionalPattern.absent();

    private Builder() {}

    public Builder applyProto(ClassItemPattern protoItem) {
      assert classNamePattern.isAny();
      if (protoItem.hasClassName()) {
        setClassNamePattern(KeepQualifiedClassNamePattern.fromProto(protoItem.getClassName()));
      }

      // TODO(b/343389186): Add instance-of.
      // TODO(b/343389186): Add annotated-by.
      return this;
    }

    public Builder copyFrom(KeepClassItemPattern pattern) {
      return setClassNamePattern(pattern.getClassNamePattern())
          .setInstanceOfPattern(pattern.getInstanceOfPattern())
          .setAnnotatedByPattern(pattern.getAnnotatedByPattern());
    }

    public Builder setClassNamePattern(KeepQualifiedClassNamePattern classNamePattern) {
      this.classNamePattern = classNamePattern;
      return this;
    }

    public Builder setInstanceOfPattern(KeepInstanceOfPattern instanceOfPattern) {
      this.instanceOfPattern = instanceOfPattern;
      return this;
    }

    public Builder setAnnotatedByPattern(
        OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern) {
      assert annotatedByPattern != null;
      this.annotatedByPattern = annotatedByPattern;
      return this;
    }

    public KeepClassItemPattern build() {
      return new KeepClassItemPattern(classNamePattern, instanceOfPattern, annotatedByPattern);
    }
  }

  private final KeepQualifiedClassNamePattern classNamePattern;
  private final KeepInstanceOfPattern instanceOfPattern;
  private final OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern;

  public KeepClassItemPattern(
      KeepQualifiedClassNamePattern classNamePattern,
      KeepInstanceOfPattern instanceOfPattern,
      OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern) {
    assert classNamePattern != null;
    assert instanceOfPattern != null;
    assert annotatedByPattern != null;
    this.classNamePattern = classNamePattern;
    this.instanceOfPattern = instanceOfPattern;
    this.annotatedByPattern = annotatedByPattern;
  }

  @Override
  public KeepClassItemPattern asClassItemPattern() {
    return this;
  }

  @Override
  public Collection<KeepBindingReference> getBindingReferences() {
    return Collections.emptyList();
  }

  public KeepQualifiedClassNamePattern getClassNamePattern() {
    return classNamePattern;
  }

  public KeepInstanceOfPattern getInstanceOfPattern() {
    return instanceOfPattern;
  }

  public OptionalPattern<KeepQualifiedClassNamePattern> getAnnotatedByPattern() {
    return annotatedByPattern;
  }

  public boolean isAny() {
    return classNamePattern.isAny() && instanceOfPattern.isAny() && annotatedByPattern.isAbsent();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof KeepClassItemPattern)) {
      return false;
    }
    KeepClassItemPattern that = (KeepClassItemPattern) obj;
    return classNamePattern.equals(that.classNamePattern)
        && instanceOfPattern.equals(that.instanceOfPattern)
        && annotatedByPattern.equals(that.annotatedByPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classNamePattern, instanceOfPattern, annotatedByPattern);
  }

  @Override
  public String toString() {
    return "KeepClassItemPattern"
        + "{ class="
        + classNamePattern
        + ", annotated-by="
        + annotatedByPattern
        + ", instance-of="
        + instanceOfPattern
        + '}';
  }
}
