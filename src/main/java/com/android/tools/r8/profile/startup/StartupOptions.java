// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup;

import static com.android.tools.r8.utils.SystemPropertyUtils.getSystemPropertyOrDefault;
import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyOrDefault;

import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SystemPropertyUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;

public class StartupOptions {

  /**
   * When enabled, attempts to move or outline all non-startup methods on startup classes.
   *
   * <p>Currently only supported in R8.
   */
  private boolean enableOutlining =
      parseSystemPropertyOrDefault("com.android.tools.r8.startup.outline", false);

  /**
   * When enabled, all startup classes will be placed in the primary classes.dex file. All other
   * (non-startup) classes will be placed in classes2.dex, ..., classesN.dex.
   */
  private boolean enableMinimalStartupDex =
      parseSystemPropertyOrDefault("com.android.tools.r8.startup.minimalstartupdex", true);

  /**
   * When enabled, optimizations crossing the startup/non-startup boundary will be allowed.
   *
   * <p>The disabling of this may help to avoid that more code may be loaded during startup as a
   * result of optimizations such as inlining and class merging.
   */
  private boolean enableStartupBoundaryOptimizations =
      parseSystemPropertyOrDefault("com.android.tools.r8.startup.boundaryoptimizations", false);

  /**
   * When enabled, each method that is not classified as a startup method at the end of compilation
   * will be changed to have a throwing method body.
   *
   * <p>This is useful for testing if a given startup list is complete (and that R8 correctly
   * rewrites the startup list in presence of optimizations).
   */
  private boolean enableStartupCompletenessCheckForTesting =
      parseSystemPropertyOrDefault("com.android.tools.r8.startup.completenesscheck", false);

  /** When enabled, the startup profile is used to layout the DEX. */
  private boolean enableStartupLayoutOptimization = true;

  /**
   * When enabled, the mixed section layout of the primary dex file will be generated using the
   * startup list, using {@link com.android.tools.r8.dex.StartupMixedSectionLayoutStrategy}.
   */
  private boolean enableStartupMixedSectionLayoutOptimizations =
      parseSystemPropertyOrDefault("com.android.tools.r8.startup.layout", true);

  private String multiStartupDexDistributionStrategyName =
      getSystemPropertyOrDefault("com.android.tools.r8.startup.multistartupdexdistribution", null);

  private Collection<StartupProfileProvider> startupProfileProviders;

  private final InternalOptions options;

  public StartupOptions(InternalOptions options) {
    this.options = options;
    this.startupProfileProviders =
        SystemPropertyUtils.applySystemProperty(
            "com.android.tools.r8.startup.profile",
            propertyValue ->
                ImmutableList.of(
                    StartupProfileProviderUtils.createFromHumanReadableArtProfile(
                        Paths.get(propertyValue))),
            Collections::emptyList);
  }

  public StartupOptions(InternalOptions options, StartupOptions startupOptions) {
    this(options);
    this.enableOutlining = startupOptions.enableOutlining;
    this.enableMinimalStartupDex = startupOptions.enableMinimalStartupDex;
    this.enableStartupBoundaryOptimizations = startupOptions.enableStartupBoundaryOptimizations;
    this.enableStartupCompletenessCheckForTesting =
        startupOptions.enableStartupCompletenessCheckForTesting;
    this.enableStartupLayoutOptimization = startupOptions.enableStartupLayoutOptimization;
    this.enableStartupMixedSectionLayoutOptimizations =
        startupOptions.enableStartupMixedSectionLayoutOptimizations;
    this.multiStartupDexDistributionStrategyName =
        startupOptions.multiStartupDexDistributionStrategyName;
    this.startupProfileProviders = startupOptions.startupProfileProviders;
  }

  public boolean isOutliningEnabled() {
    return enableOutlining;
  }

  public boolean isMinimalStartupDexEnabled() {
    return enableMinimalStartupDex;
  }

  public StartupOptions setEnableMinimalStartupDex(boolean enableMinimalStartupDex) {
    this.enableMinimalStartupDex = enableMinimalStartupDex;
    return this;
  }

  public boolean isStartupBoundaryOptimizationsEnabled() {
    return enableStartupBoundaryOptimizations;
  }

  public StartupOptions setEnableStartupBoundaryOptimizations(
      boolean enableStartupBoundaryOptimizations) {
    this.enableStartupBoundaryOptimizations = enableStartupBoundaryOptimizations;
    return this;
  }

  public boolean isStartupMixedSectionLayoutOptimizationsEnabled() {
    return enableStartupMixedSectionLayoutOptimizations;
  }

  public boolean isStartupCompletenessCheckForTestingEnabled() {
    return enableStartupCompletenessCheckForTesting;
  }

  public boolean isStartupLayoutOptimizationEnabled() {
    return enableStartupLayoutOptimization;
  }

  public StartupOptions setEnableOutlining(boolean enableOutlining) {
    this.enableOutlining = enableOutlining;
    return this;
  }

  public StartupOptions setEnableStartupCompletenessCheckForTesting() {
    return setEnableStartupCompletenessCheckForTesting(true);
  }

  public StartupOptions setEnableStartupCompletenessCheckForTesting(
      boolean enableStartupCompletenessCheckForTesting) {
    this.enableStartupCompletenessCheckForTesting = enableStartupCompletenessCheckForTesting;
    return this;
  }

  public StartupOptions setEnableStartupLayoutOptimization(
      boolean enableStartupLayoutOptimization) {
    this.enableStartupLayoutOptimization = enableStartupLayoutOptimization;
    return this;
  }

  public String getMultiStartupDexDistributionStrategyName() {
    return multiStartupDexDistributionStrategyName;
  }

  public boolean hasStartupProfileProviders() {
    return startupProfileProviders != null && !startupProfileProviders.isEmpty();
  }

  public Collection<StartupProfileProvider> getStartupProfileProviders() {
    assert options.partialSubCompilationConfiguration == null
        || options.partialSubCompilationConfiguration.isD8();
    return startupProfileProviders;
  }

  public StartupOptions setStartupProfileProviders(
      Collection<StartupProfileProvider> startupProfileProviders) {
    this.startupProfileProviders = startupProfileProviders;
    return this;
  }
}
