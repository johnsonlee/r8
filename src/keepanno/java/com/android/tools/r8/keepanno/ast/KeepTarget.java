// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Objects;

public class KeepTarget {

  public static class Builder {

    private KeepItemReference item;
    private KeepConstraints constraints = KeepConstraints.defaultConstraints();

    private Builder() {}

    public Builder setItemReference(KeepItemReference item) {
      this.item = item;
      return this;
    }

    public Builder setItemPattern(KeepItemPattern itemPattern) {
      return setItemReference(itemPattern.toItemReference());
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

  private final KeepItemReference item;
  private final KeepConstraints constraints;

  private KeepTarget(KeepItemReference item, KeepConstraints constraints) {
    assert item != null;
    assert constraints != null;
    this.item = item;
    this.constraints = constraints;
  }

  public static Builder builder() {
    return new Builder();
  }

  public KeepItemReference getItem() {
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
