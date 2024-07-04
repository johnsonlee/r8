// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos.ClassPattern;
import java.util.Objects;
import java.util.function.Consumer;

public class KeepClassPattern {

  private static final KeepClassPattern ANY =
      new KeepClassPattern(KeepQualifiedClassNamePattern.any(), KeepInstanceOfPattern.any());

  public static KeepClassPattern any() {
    return ANY;
  }

  public static KeepClassPattern fromName(KeepQualifiedClassNamePattern namePattern) {
    return builder().setClassNamePattern(namePattern).build();
  }

  public static KeepClassPattern fromProto(ClassPattern proto) {
    return builder().applyProto(proto).build();
  }

  public static KeepClassPattern exactFromDescriptor(String typeDescriptor) {
    return fromName(KeepQualifiedClassNamePattern.exactFromDescriptor(typeDescriptor));
  }

  private final KeepQualifiedClassNamePattern classNamePattern;
  private final KeepInstanceOfPattern instanceOfPattern;

  public KeepClassPattern(
      KeepQualifiedClassNamePattern classNamePattern, KeepInstanceOfPattern instanceOfPattern) {
    this.classNamePattern = classNamePattern;
    this.instanceOfPattern = instanceOfPattern;
  }

  public boolean isAny() {
    return classNamePattern.isAny() && instanceOfPattern.isAny();
  }

  public KeepQualifiedClassNamePattern getClassNamePattern() {
    return classNamePattern;
  }

  public KeepInstanceOfPattern getInstanceOfPattern() {
    return instanceOfPattern;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void buildProtoIfNotAny(Consumer<ClassPattern.Builder> setter) {
    if (!isAny()) {
      setter.accept(buildProto());
    }
  }

  public ClassPattern.Builder buildProto() {
    ClassPattern.Builder builder = ClassPattern.newBuilder();
    classNamePattern.buildProtoIfNotAny(builder::setClassName);
    instanceOfPattern.buildProtoIfNotAny(builder::setInstanceOf);
    return builder;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof KeepClassPattern)) {
      return false;
    }
    KeepClassPattern other = (KeepClassPattern) obj;
    return classNamePattern.equals(other.classNamePattern)
        && instanceOfPattern.equals(other.instanceOfPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classNamePattern, instanceOfPattern);
  }

  public static class Builder {

    private KeepQualifiedClassNamePattern classNamePattern = KeepQualifiedClassNamePattern.any();
    private KeepInstanceOfPattern instanceOfPattern = KeepInstanceOfPattern.any();

    public Builder applyProto(ClassPattern protoItem) {
      assert classNamePattern.isAny();
      if (protoItem.hasClassName()) {
        setClassNamePattern(KeepQualifiedClassNamePattern.fromProto(protoItem.getClassName()));
      }
      assert instanceOfPattern.isAny();
      if (protoItem.hasInstanceOf()) {
        setInstanceOfPattern(KeepInstanceOfPattern.fromProto(protoItem.getInstanceOf()));
      }
      return this;
    }

    public Builder copyFrom(KeepClassPattern pattern) {
      return setClassNamePattern(pattern.getClassNamePattern())
          .setInstanceOfPattern(pattern.getInstanceOfPattern());
    }

    public Builder setClassNamePattern(KeepQualifiedClassNamePattern classNamePattern) {
      this.classNamePattern = classNamePattern;
      return this;
    }

    public Builder setInstanceOfPattern(KeepInstanceOfPattern instanceOfPattern) {
      this.instanceOfPattern = instanceOfPattern;
      return this;
    }

    public KeepClassPattern build() {
      if (classNamePattern.isAny() && instanceOfPattern.isAny()) {
        return any();
      }
      return new KeepClassPattern(classNamePattern, instanceOfPattern);
    }
  }
}
