// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyOrDefault;

import com.android.tools.r8.utils.OptionalBool;

public class ProguardConfigurationParserOptions {

  private final OptionalBool enableEmptyMemberRulesToDefaultInitRuleConversion;
  private final boolean enableExperimentalCheckEnumUnboxed;
  private final boolean enableExperimentalConvertCheckNotNull;
  private final boolean enableExperimentalWhyAreYouNotInlining;
  private final boolean enableTestingOptions;

  ProguardConfigurationParserOptions(
      OptionalBool enableEmptyMemberRulesToDefaultInitRuleConversion,
      boolean enableExperimentalCheckEnumUnboxed,
      boolean enableExperimentalConvertCheckNotNull,
      boolean enableExperimentalWhyAreYouNotInlining,
      boolean enableTestingOptions) {
    this.enableExperimentalCheckEnumUnboxed = enableExperimentalCheckEnumUnboxed;
    this.enableExperimentalConvertCheckNotNull = enableExperimentalConvertCheckNotNull;
    this.enableExperimentalWhyAreYouNotInlining = enableExperimentalWhyAreYouNotInlining;
    this.enableTestingOptions = enableTestingOptions;
    this.enableEmptyMemberRulesToDefaultInitRuleConversion =
        enableEmptyMemberRulesToDefaultInitRuleConversion;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEmptyMemberRulesToDefaultInitRuleConversionEnabled(
      ProguardConfiguration.Builder configurationBuilder) {
    // TODO(b/356344563): Disable in full mode in the next major version.
    return configurationBuilder.isForceProguardCompatibility()
        || enableEmptyMemberRulesToDefaultInitRuleConversion.getOrDefault(true);
  }

  public boolean isEmptyMemberRulesToDefaultInitRuleConversionWarningsEnabled(
      ProguardConfiguration.Builder configurationBuilder) {
    assert isEmptyMemberRulesToDefaultInitRuleConversionEnabled(configurationBuilder);
    return !configurationBuilder.isForceProguardCompatibility()
        && enableEmptyMemberRulesToDefaultInitRuleConversion.isUnknown();
  }

  public boolean isExperimentalCheckEnumUnboxedEnabled() {
    return enableExperimentalCheckEnumUnboxed;
  }

  public boolean isExperimentalConvertCheckNotNullEnabled() {
    return enableExperimentalConvertCheckNotNull;
  }

  public boolean isExperimentalWhyAreYouNotInliningEnabled() {
    return enableExperimentalWhyAreYouNotInlining;
  }

  public boolean isTestingOptionsEnabled() {
    return enableTestingOptions;
  }

  public static class Builder {

    private OptionalBool enableEmptyMemberRulesToDefaultInitRuleConversion = OptionalBool.UNKNOWN;
    private boolean enableExperimentalCheckEnumUnboxed;
    private boolean enableExperimentalConvertCheckNotNull;
    private boolean enableExperimentalWhyAreYouNotInlining;
    private boolean enableTestingOptions;

    public Builder readEnvironment() {
      enableEmptyMemberRulesToDefaultInitRuleConversion =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.enableEmptyMemberRulesToDefaultInitRuleConversion",
              OptionalBool.UNKNOWN);
      enableExperimentalCheckEnumUnboxed =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.experimental.enablecheckenumunboxed", false);
      enableExperimentalConvertCheckNotNull =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.experimental.enableconvertchecknotnull", false);
      enableExperimentalWhyAreYouNotInlining =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.experimental.enablewhyareyounotinlining", false);
      enableTestingOptions =
          parseSystemPropertyOrDefault("com.android.tools.r8.allowTestProguardOptions", false);
      return this;
    }

    public Builder setEnableEmptyMemberRulesToDefaultInitRuleConversion(
        boolean enableEmptyMemberRulesToDefaultInitRuleConversion) {
      this.enableEmptyMemberRulesToDefaultInitRuleConversion =
          OptionalBool.of(enableEmptyMemberRulesToDefaultInitRuleConversion);
      return this;
    }

    public Builder setEnableExperimentalCheckEnumUnboxed(
        boolean enableExperimentalCheckEnumUnboxed) {
      this.enableExperimentalCheckEnumUnboxed = enableExperimentalCheckEnumUnboxed;
      return this;
    }

    public Builder setEnableExperimentalConvertCheckNotNull(
        boolean enableExperimentalConvertCheckNotNull) {
      this.enableExperimentalConvertCheckNotNull = enableExperimentalConvertCheckNotNull;
      return this;
    }

    public Builder setEnableExperimentalWhyAreYouNotInlining(
        boolean enableExperimentalWhyAreYouNotInlining) {
      this.enableExperimentalWhyAreYouNotInlining = enableExperimentalWhyAreYouNotInlining;
      return this;
    }

    public Builder setEnableTestingOptions(boolean enableTestingOptions) {
      this.enableTestingOptions = enableTestingOptions;
      return this;
    }

    public ProguardConfigurationParserOptions build() {
      return new ProguardConfigurationParserOptions(
          enableEmptyMemberRulesToDefaultInitRuleConversion,
          enableExperimentalCheckEnumUnboxed,
          enableExperimentalConvertCheckNotNull,
          enableExperimentalWhyAreYouNotInlining,
          enableTestingOptions);
    }
  }
}
