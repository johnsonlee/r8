// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.keeprules.RulePrinter;
import com.android.tools.r8.keepanno.keeprules.RulePrintingUtils;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.MemberAccessField;
import java.util.Set;
import java.util.function.Consumer;

public class KeepFieldAccessPattern extends KeepMemberAccessPattern {

  private static final KeepFieldAccessPattern ANY =
      new KeepFieldAccessPattern(
          AccessVisibility.all(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any(),
          ModifierPattern.any());

  public static KeepFieldAccessPattern anyFieldAccess() {
    return ANY;
  }

  public static Builder builder() {
    return new Builder();
  }

  private final ModifierPattern volatilePattern;
  private final ModifierPattern transientPattern;

  public KeepFieldAccessPattern(
      Set<AccessVisibility> allowedVisibilities,
      ModifierPattern staticPattern,
      ModifierPattern finalPattern,
      ModifierPattern volatilePattern,
      ModifierPattern transientPattern,
      ModifierPattern syntheticPattern) {
    super(allowedVisibilities, staticPattern, finalPattern, syntheticPattern);
    this.volatilePattern = volatilePattern;
    this.transientPattern = transientPattern;
  }

  public static KeepFieldAccessPattern fromFieldProto(MemberAccessField proto) {
    return builder().applyFieldProto(proto).build();
  }

  @Override
  public String toString() {
    if (isAny()) {
      return "*";
    }
    StringBuilder builder = new StringBuilder();
    RulePrintingUtils.printFieldAccess(RulePrinter.withoutBackReferences(builder), this);
    return builder.toString();
  }

  @Override
  public boolean isAny() {
    return super.isAny() && volatilePattern.isAny() && transientPattern.isAny();
  }

  public ModifierPattern getVolatilePattern() {
    return volatilePattern;
  }

  public ModifierPattern getTransientPattern() {
    return transientPattern;
  }

  public void buildFieldProto(Consumer<MemberAccessField.Builder> callback) {
    if (isAny()) {
      return;
    }
    MemberAccessField.Builder builder = MemberAccessField.newBuilder();
    buildGeneralProto(builder::setGeneralAccess);
    volatilePattern.buildProtoIfNotAny(builder::setVolatilePattern);
    transientPattern.buildProtoIfNotAny(builder::setTransientPattern);
    callback.accept(builder);
  }

  public static class Builder extends BuilderBase<KeepFieldAccessPattern, Builder> {

    private ModifierPattern volatilePattern = ModifierPattern.any();
    private ModifierPattern transientPattern = ModifierPattern.any();

    private Builder() {}

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public KeepFieldAccessPattern build() {
      KeepFieldAccessPattern pattern =
          new KeepFieldAccessPattern(
              getAllowedVisibilities(),
              getStaticPattern(),
              getFinalPattern(),
              getVolatilePattern(),
              getTransientPattern(),
              getSyntheticPattern());
      return pattern.isAny() ? anyFieldAccess() : pattern;
    }

    public Builder setVolatile(boolean allow) {
      volatilePattern = ModifierPattern.fromAllowValue(allow);
      return self();
    }

    public Builder setTransient(boolean allow) {
      transientPattern = ModifierPattern.fromAllowValue(allow);
      return self();
    }

    public ModifierPattern getVolatilePattern() {
      return volatilePattern;
    }

    public ModifierPattern getTransientPattern() {
      return transientPattern;
    }

    public Builder applyFieldProto(MemberAccessField proto) {
      if (proto.hasGeneralAccess()) {
        applyGeneralProto(proto.getGeneralAccess());
      }
      assert volatilePattern.isAny();
      if (proto.hasVolatilePattern()) {
        setVolatile(proto.getVolatilePattern().getValue());
      }
      assert transientPattern.isAny();
      if (proto.hasTransientPattern()) {
        setTransient(proto.getTransientPattern().getValue());
      }
      return this;
    }
  }
}
