// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.google.common.base.Splitter;
import java.util.Iterator;
import java.util.List;

/** Immutable keep requirements for a field. */
public final class KeepFieldInfo extends KeepMemberInfo<KeepFieldInfo.Builder, KeepFieldInfo> {

  // Requires all aspects of a field to be kept.
  private static final KeepFieldInfo TOP = new Builder().makeTop().build();

  // Requires no aspects of a field to be kept.
  private static final KeepFieldInfo BOTTOM = new Builder().makeBottom().build();

  public static KeepFieldInfo top() {
    return TOP;
  }

  public static KeepFieldInfo bottom() {
    return BOTTOM;
  }

  public static Joiner newEmptyJoiner() {
    return bottom().joiner();
  }

  private final boolean allowFieldTypeStrengthening;
  private final boolean allowRedundantFieldLoadElimination;

  private KeepFieldInfo(Builder builder) {
    super(builder);
    this.allowFieldTypeStrengthening = builder.isFieldTypeStrengtheningAllowed();
    this.allowRedundantFieldLoadElimination = builder.isRedundantFieldLoadEliminationAllowed();
  }

  // This builder is not private as there are known instances where it is safe to modify keep info
  // in a non-upwards direction.
  @Override
  Builder builder() {
    return new Builder(this);
  }

  public boolean isFieldTypeStrengtheningAllowed(GlobalKeepInfoConfiguration configuration) {
    return internalIsFieldTypeStrengtheningAllowed();
  }

  boolean internalIsFieldTypeStrengtheningAllowed() {
    return allowFieldTypeStrengthening;
  }

  public boolean isRedundantFieldLoadEliminationAllowed(GlobalKeepInfoConfiguration configuration) {
    return internalIsRedundantFieldLoadEliminationAllowed();
  }

  boolean internalIsRedundantFieldLoadEliminationAllowed() {
    return allowRedundantFieldLoadElimination;
  }

  public Joiner joiner() {
    assert !isTop();
    return new Joiner(this);
  }

  @Override
  public boolean isTop() {
    return this.equals(top());
  }

  @Override
  public boolean isBottom() {
    return this.equals(bottom());
  }

  private boolean internalBooleanEquals(KeepFieldInfo other) {
    return allowFieldTypeStrengthening == other.internalIsFieldTypeStrengtheningAllowed()
        && allowRedundantFieldLoadElimination
            == other.internalIsRedundantFieldLoadEliminationAllowed();
  }

  @Override
  public boolean equalsWithAnnotations(KeepFieldInfo other) {
    return super.equalsWithAnnotations(other) && internalBooleanEquals(other);
  }

  @Override
  public boolean equalsNoAnnotations(KeepFieldInfo other) {
    return super.equalsNoAnnotations(other) && internalBooleanEquals(other);
  }

  @Override
  public int hashCodeNoAnnotations() {
    int hash = super.hashCodeNoAnnotations();
    int index = super.numberOfBooleans();
    hash += bit(allowFieldTypeStrengthening, index++);
    hash += bit(allowRedundantFieldLoadElimination, index);
    return hash;
  }

  public static KeepFieldInfo parse(Iterator<String> iterator) {
    Builder builder = new Builder().makeBottom();
    while (iterator.hasNext()) {
      String next = iterator.next();
      if (next.equals("")) {
        return new KeepFieldInfo(builder);
      }
      List<String> split = Splitter.on(": ").splitToList(next);
      assert split.size() == 2;
      String key = split.get(0);
      String value = split.get(1);
      if (KeepMemberInfo.handle(key, value, builder)) {
        continue;
      }
      if (key.equals("allowFieldTypeStrengthening")) {
        builder.setAllowFieldTypeStrengthening(Boolean.parseBoolean(value));
      } else if (key.equals("allowRedundantFieldLoadElimination")) {
        builder.setAllowRedundantFieldLoadElimination(Boolean.parseBoolean(value));
      } else {
        assert false;
      }
    }
    return new KeepFieldInfo(builder);
  }

  @Override
  public List<String> lines() {
    List<String> lines = linesDifferentFromBase(bottom());
    if (bottom().allowFieldTypeStrengthening != allowFieldTypeStrengthening) {
      lines.add("allowFieldTypeStrengthening: " + allowFieldTypeStrengthening);
    }
    if (bottom().allowRedundantFieldLoadElimination != allowRedundantFieldLoadElimination) {
      lines.add("allowRedundantFieldLoadElimination: " + allowRedundantFieldLoadElimination);
    }
    return lines;
  }

  public static class Builder extends KeepMemberInfo.Builder<Builder, KeepFieldInfo> {

    private boolean allowFieldTypeStrengthening;
    private boolean allowRedundantFieldLoadElimination;

    private Builder() {
      super();
    }

    private Builder(KeepFieldInfo original) {
      super(original);
      allowFieldTypeStrengthening = original.internalIsFieldTypeStrengtheningAllowed();
      allowRedundantFieldLoadElimination =
          original.internalIsRedundantFieldLoadEliminationAllowed();
    }

    @Override
    public Builder makeTop() {
      return super.makeTop()
          .setAllowFieldTypeStrengthening(false)
          .setAllowRedundantFieldLoadElimination(false);
    }

    @Override
    public Builder makeBottom() {
      return super.makeBottom()
          .setAllowFieldTypeStrengthening(true)
          .setAllowRedundantFieldLoadElimination(true);
    }

    public boolean isFieldTypeStrengtheningAllowed() {
      return allowFieldTypeStrengthening;
    }

    public Builder setAllowFieldTypeStrengthening(boolean allowFieldTypeStrengthening) {
      this.allowFieldTypeStrengthening = allowFieldTypeStrengthening;
      return self();
    }

    public boolean isRedundantFieldLoadEliminationAllowed() {
      return allowRedundantFieldLoadElimination;
    }

    public Builder setAllowRedundantFieldLoadElimination(
        boolean allowRedundantFieldLoadElimination) {
      this.allowRedundantFieldLoadElimination = allowRedundantFieldLoadElimination;
      return self();
    }

    @Override
    public KeepFieldInfo getTopInfo() {
      return TOP;
    }

    @Override
    public KeepFieldInfo getBottomInfo() {
      return BOTTOM;
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public boolean isEqualTo(KeepFieldInfo other) {
      return internalIsEqualTo(other);
    }

    @Override
    boolean internalIsEqualTo(KeepFieldInfo other) {
      return super.internalIsEqualTo(other)
          && isFieldTypeStrengtheningAllowed() == other.internalIsFieldTypeStrengtheningAllowed()
          && isRedundantFieldLoadEliminationAllowed()
              == other.internalIsRedundantFieldLoadEliminationAllowed();
    }

    @Override
    public KeepFieldInfo doBuild() {
      return new KeepFieldInfo(this);
    }
  }

  public static class Joiner extends KeepMemberInfo.Joiner<Joiner, Builder, KeepFieldInfo> {

    public Joiner(KeepFieldInfo info) {
      super(info.builder());
    }

    public Joiner disallowFieldTypeStrengthening() {
      builder.setAllowFieldTypeStrengthening(false);
      return self();
    }

    public Joiner disallowRedundantFieldLoadElimination() {
      builder.setAllowRedundantFieldLoadElimination(false);
      return self();
    }

    @Override
    public Joiner asFieldJoiner() {
      return this;
    }

    @Override
    public Joiner merge(Joiner joiner) {
      // Should be extended to merge the fields of this class in case any are added.
      return super.merge(joiner)
          .applyIf(
              !joiner.builder.isFieldTypeStrengtheningAllowed(),
              KeepFieldInfo.Joiner::disallowFieldTypeStrengthening)
          .applyIf(
              !joiner.builder.isRedundantFieldLoadEliminationAllowed(),
              KeepFieldInfo.Joiner::disallowRedundantFieldLoadElimination);
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
