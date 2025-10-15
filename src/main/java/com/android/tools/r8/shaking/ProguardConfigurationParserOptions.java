// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyOrDefault;

public class ProguardConfigurationParserOptions {

  private final boolean enableLegacyFullModeForKeepRules;
  private final boolean enableLegacyFullModeForKeepRulesWarnings;
  private final boolean enableKeepRuntimeInvisibleAnnotations;
  private final boolean enableTestingOptions;
  private final boolean forceProguardCompatibility;

  ProguardConfigurationParserOptions(
      boolean enableLegacyFullModeForKeepRules,
      boolean enableLegacyFullModeForKeepRulesWarnings,
      boolean enableKeepRuntimeInvisibleAnnotations,
      boolean enableTestingOptions,
      boolean forceProguardCompatibility) {
    this.enableKeepRuntimeInvisibleAnnotations = enableKeepRuntimeInvisibleAnnotations;
    this.enableTestingOptions = enableTestingOptions;
    this.enableLegacyFullModeForKeepRules = enableLegacyFullModeForKeepRules;
    this.enableLegacyFullModeForKeepRulesWarnings = enableLegacyFullModeForKeepRulesWarnings;
    this.forceProguardCompatibility = forceProguardCompatibility;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isLegacyFullModeForKeepRulesEnabled() {
    // TODO(b/356344563): Disable in full mode in the next major version.
    return forceProguardCompatibility || enableLegacyFullModeForKeepRules;
  }

  public boolean isLegacyFullModeForKeepRulesWarningsEnabled() {
    assert isLegacyFullModeForKeepRulesEnabled();
    return !forceProguardCompatibility && enableLegacyFullModeForKeepRulesWarnings;
  }

  public boolean isKeepRuntimeInvisibleAnnotationsEnabled() {
    return enableKeepRuntimeInvisibleAnnotations;
  }

  public boolean isTestingOptionsEnabled() {
    return enableTestingOptions;
  }

  public static class Builder {

    private boolean enableLegacyFullModeForKeepRules = true;
    private boolean enableLegacyFullModeForKeepRulesWarnings = false;
    private boolean enableKeepRuntimeInvisibleAnnotations = true;
    private boolean enableTestingOptions;
    private boolean forceProguardCompatibility = false;

    public Builder readEnvironment() {
      enableLegacyFullModeForKeepRules =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.enableLegacyFullModeForKeepRules", true);
      enableLegacyFullModeForKeepRulesWarnings =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.enableLegacyFullModeForKeepRulesWarnings", false);
      enableKeepRuntimeInvisibleAnnotations =
          parseSystemPropertyOrDefault(
              "com.android.tools.r8.enableKeepRuntimeInvisibleAnnotations", true);
      enableTestingOptions =
          parseSystemPropertyOrDefault("com.android.tools.r8.allowTestProguardOptions", false);
      return this;
    }

    public Builder setEnableLegacyFullModeForKeepRules(boolean enableLegacyFullModeForKeepRules) {
      this.enableLegacyFullModeForKeepRules = enableLegacyFullModeForKeepRules;
      return this;
    }

    public Builder setEnableLegacyFullModeForKeepRulesWarnings(
        boolean enableLegacyFullModeForKeepRulesWarnings) {
      this.enableLegacyFullModeForKeepRulesWarnings = enableLegacyFullModeForKeepRulesWarnings;
      return this;
    }

    public Builder setEnableTestingOptions(boolean enableTestingOptions) {
      this.enableTestingOptions = enableTestingOptions;
      return this;
    }

    public Builder setForceProguardCompatibility(boolean forceProguardCompatibility) {
      this.forceProguardCompatibility = forceProguardCompatibility;
      return this;
    }

    public ProguardConfigurationParserOptions build() {
      return new ProguardConfigurationParserOptions(
          enableLegacyFullModeForKeepRules,
          enableLegacyFullModeForKeepRulesWarnings,
          enableKeepRuntimeInvisibleAnnotations,
          enableTestingOptions,
          forceProguardCompatibility);
    }
  }
}
