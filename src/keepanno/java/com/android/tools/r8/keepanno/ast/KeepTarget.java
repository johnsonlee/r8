// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepSpecUtils.BindingResolver;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.Target;
import java.util.Objects;

public class KeepTarget {

  public Target.Builder buildProto() {
    Target.Builder builder = Target.newBuilder();
    constraints.buildProto(builder::setConstraints, builder::addConstraintAdditions);
    return builder.setItem(item.buildProto());
  }

  public static KeepTarget fromProto(Target proto, BindingResolver resolver) {
    return builder().applyProto(proto, resolver).build();
  }

  public static class Builder {

    private KeepBindingReference item;
    private KeepConstraints constraints = KeepConstraints.defaultConstraints();

    private Builder() {}

    public Builder applyProto(Target proto, BindingResolver resolver) {
      setItemReference(resolver.mapReference(proto.getItem()));
      assert constraints.isDefault();
      constraints.fromProto(
          proto.hasConstraints() ? proto.getConstraints() : null,
          proto.getConstraintAdditionsList(),
          this::setConstraints);
      return this;
    }

    public Builder setItemReference(KeepBindingReference itemReference) {
      this.item = itemReference;
      return this;
    }

    public Builder setConstraints(KeepConstraints constraints) {
      this.constraints = constraints;
      return this;
    }

    public KeepTarget build() {
      if (item == null) {
        throw new KeepEdgeException("Target must define an item pattern");
      }
      return new KeepTarget(item, constraints);
    }
  }

  private final KeepBindingReference item;
  private final KeepConstraints constraints;

  private KeepTarget(KeepBindingReference item, KeepConstraints constraints) {
    assert item != null;
    assert constraints != null;
    this.item = item;
    this.constraints = constraints;
  }

  public static Builder builder() {
    return new Builder();
  }

  public KeepBindingReference getItem() {
    return item;
  }

  public KeepConstraints getConstraints() {
    return constraints;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepTarget)) {
      return false;
    }
    KeepTarget that = (KeepTarget) o;
    return item.equals(that.item) && constraints.equals(that.constraints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(item, constraints);
  }

  @Override
  public String toString() {
    return "KeepTarget{" + "item=" + item + ", constraints=" + constraints + '}';
  }
}
