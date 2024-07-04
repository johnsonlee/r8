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
    ClassItemPattern.Builder builder = ClassItemPattern.newBuilder();
    classPattern.buildProtoIfNotAny(builder::setClassPattern);
    KeepSpecUtils.buildAnnotatedByProto(annotatedByPattern, builder::setAnnotatedBy);
    return builder;
  }

  public static class Builder {

    private KeepClassPattern.Builder classPattern = KeepClassPattern.builder();
    private OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern =
        OptionalPattern.absent();

    private Builder() {}

    public Builder applyProto(ClassItemPattern protoItem) {
      assert classPattern.build().isAny();
      if (protoItem.hasClassPattern()) {
        setClassPattern(KeepClassPattern.fromProto(protoItem.getClassPattern()));
      }
      assert annotatedByPattern.isAbsent();
      if (protoItem.hasAnnotatedBy()) {
        setAnnotatedByPattern(KeepSpecUtils.annotatedByFromProto(protoItem.getAnnotatedBy()));
      }
      return this;
    }

    public Builder copyFrom(KeepClassItemPattern pattern) {
      return setClassPattern(pattern.getClassPattern())
          .setAnnotatedByPattern(pattern.getAnnotatedByPattern());
    }

    public Builder setClassPattern(KeepClassPattern classPattern) {
      this.classPattern = KeepClassPattern.builder().copyFrom(classPattern);
      return this;
    }

    public Builder setClassNamePattern(KeepQualifiedClassNamePattern classNamePattern) {
      classPattern.setClassNamePattern(classNamePattern);
      return this;
    }

    public Builder setInstanceOfPattern(KeepInstanceOfPattern instanceOfPattern) {
      classPattern.setInstanceOfPattern(instanceOfPattern);
      return this;
    }

    public Builder setAnnotatedByPattern(
        OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern) {
      assert annotatedByPattern != null;
      this.annotatedByPattern = annotatedByPattern;
      return this;
    }

    public KeepClassItemPattern build() {
      return new KeepClassItemPattern(classPattern.build(), annotatedByPattern);
    }
  }

  private final KeepClassPattern classPattern;
  private final OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern;

  public KeepClassItemPattern(
      KeepClassPattern classPattern,
      OptionalPattern<KeepQualifiedClassNamePattern> annotatedByPattern) {
    assert classPattern != null;
    assert annotatedByPattern != null;
    this.classPattern = classPattern;
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

  public KeepClassPattern getClassPattern() {
    return classPattern;
  }

  public KeepQualifiedClassNamePattern getClassNamePattern() {
    return classPattern.getClassNamePattern();
  }

  public KeepInstanceOfPattern getInstanceOfPattern() {
    return classPattern.getInstanceOfPattern();
  }

  public OptionalPattern<KeepQualifiedClassNamePattern> getAnnotatedByPattern() {
    return annotatedByPattern;
  }

  public boolean isAny() {
    return classPattern.isAny() && annotatedByPattern.isAbsent();
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
    return classPattern.equals(that.classPattern)
        && annotatedByPattern.equals(that.annotatedByPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classPattern, annotatedByPattern);
  }

  @Override
  public String toString() {
    return "KeepClassItemPattern"
        + "{ class="
        + classPattern
        + ", annotated-by="
        + annotatedByPattern
        + '}';
  }
}
