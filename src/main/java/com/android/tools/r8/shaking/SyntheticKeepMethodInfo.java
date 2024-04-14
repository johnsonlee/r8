// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

public class SyntheticKeepMethodInfo extends KeepMethodInfo {

  // Requires no aspects of a method to be kept.
  private static final SyntheticKeepMethodInfo BOTTOM = new Builder().makeBottom().build();

  public static SyntheticKeepMethodInfo bottom() {
    return BOTTOM;
  }

  public static Joiner newEmptyJoiner() {
    return bottom().joiner();
  }

  @Override
  Builder builder() {
    return new Builder(this);
  }

  public static class Builder extends KeepMethodInfo.Builder {

    public Builder() {
      super();
    }

    private Builder(SyntheticKeepMethodInfo original) {
      super(original);
    }

    @Override
    public Builder disallowMinification() {
      // Ignore as synthetic items can always be minified.
      return self();
    }

    @Override
    public Builder disallowOptimization() {
      // Ignore as synthetic items can always be optimized.
      return self();
    }

    @Override
    public Builder disallowShrinking() {
      // Ignore as synthetic items can always be removed.
      return self();
    }

    @Override
    public SyntheticKeepMethodInfo doBuild() {
      return new SyntheticKeepMethodInfo(this);
    }

    @Override
    public SyntheticKeepMethodInfo build() {
      return (SyntheticKeepMethodInfo) super.build();
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

  public static class Joiner extends KeepMethodInfo.Joiner {
    public Joiner(SyntheticKeepMethodInfo info) {
      super(info.builder());
    }

    @Override
    public KeepMethodInfo.Joiner disallowMinification() {
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

  public SyntheticKeepMethodInfo(Builder builder) {
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
