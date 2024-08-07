// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

public class SyntheticKeepClassInfo extends KeepClassInfo {

  // Requires no aspects of a method to be kept.
  private static final SyntheticKeepClassInfo BOTTOM = new Builder().makeBottom().build();

  public static SyntheticKeepClassInfo bottom() {
    return BOTTOM;
  }

  public static Joiner newEmptyJoiner() {
    return bottom().joiner();
  }

  @Override
  Builder builder() {
    return new Builder(this);
  }

  public static class Builder extends KeepClassInfo.Builder {

    public Builder() {
      super();
    }

    private Builder(SyntheticKeepClassInfo original) {
      super(original);
    }

    @Override
    public boolean isMinificationAllowed() {
      // Synthetic items can always be minified.
      return true;
    }

    @Override
    public boolean isOptimizationAllowed() {
      // Synthetic items can always be optimized.
      return true;
    }

    @Override
    public boolean isShrinkingAllowed() {
      // Synthetic items can always be removed.
      return true;
    }

    @Override
    public SyntheticKeepClassInfo doBuild() {
      return new SyntheticKeepClassInfo(this);
    }

    @Override
    public SyntheticKeepClassInfo build() {
      return (SyntheticKeepClassInfo) super.build();
    }

    @Override
    public Builder makeBottom() {
      super.makeBottom();
      return self();
    }

    @Override
    public Builder self() {
      return this;
    }
  }

  public static class Joiner extends KeepClassInfo.Joiner {

    public Joiner(SyntheticKeepClassInfo info) {
      super(info.builder());
    }

    @Override
    public Joiner disallowMinification() {
      // Ignore as synthetic items can always be minified.
      return self();
    }

    @Override
    public Joiner disallowOptimization() {
      // Ignore as synthetic items can always be optimized.
      return self();
    }

    @Override
    public Joiner disallowShrinking() {
      // Ignore as synthetic items can always be removed.
      return self();
    }

    @Override
    Joiner self() {
      return this;
    }
  }

  public SyntheticKeepClassInfo(Builder builder) {
    super(builder);
  }

  @Override
  public boolean isMinificationAllowed(GlobalKeepInfoConfiguration configuration) {
    // Synthetic items can always be minified.
    return true;
  }

  @Override
  public boolean isOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    // Synthetic items can always be minified.
    return true;
  }

  @Override
  public boolean isShrinkingAllowed(GlobalKeepInfoConfiguration configuration) {
    // Synthetic items can always be minified.
    return true;
  }

  @Override
  public Joiner joiner() {
    return new Joiner(this);
  }
}
