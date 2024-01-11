// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class KeepConstraints {

  public static KeepConstraints fromLegacyOptions(KeepOptions options) {
    return new LegacyOptions(options);
  }

  public static KeepConstraints defaultConstraints() {
    return Defaults.INSTANCE;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final Set<KeepConstraint> constraints = new HashSet<>();

    private Builder() {}

    public Builder add(KeepConstraint constraint) {
      constraints.add(constraint);
      return this;
    }

    public KeepConstraints build() {
      return new Constraints(constraints);
    }
  }

  private static class LegacyOptions extends KeepConstraints {

    private final KeepOptions options;

    private LegacyOptions(KeepOptions options) {
      this.options = options;
    }

    @Override
    public String toString() {
      return options.toString();
    }

    @Override
    public KeepOptions convertToKeepOptions(KeepOptions defaultOptions) {
      return options;
    }
  }

  private static class Defaults extends KeepConstraints {

    private static final Defaults INSTANCE = new Defaults();

    @Override
    public KeepOptions convertToKeepOptions(KeepOptions defaultOptions) {
      return defaultOptions;
    }

    @Override
    public String toString() {
      return "KeepConstraints.Defaults{}";
    }
  }

  private static class Constraints extends KeepConstraints {

    private final Set<KeepConstraint> constraints;

    public Constraints(Set<KeepConstraint> constraints) {
      this.constraints = ImmutableSet.copyOf(constraints);
    }

    @Override
    public KeepOptions convertToKeepOptions(KeepOptions defaultOptions) {
      KeepOptions.Builder builder = KeepOptions.disallowBuilder();
      for (KeepConstraint constraint : constraints) {
        constraint.convertToDisallowKeepOptions(builder);
      }
      return builder.build();
    }

    @Override
    public String toString() {
      return "KeepConstraints{"
          + constraints.stream().map(Objects::toString).collect(Collectors.joining(", "))
          + '}';
    }
  }

  public abstract KeepOptions convertToKeepOptions(KeepOptions defaultOptions);
}
