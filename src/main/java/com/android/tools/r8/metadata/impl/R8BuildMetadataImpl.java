// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;


import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8BaselineProfileRewritingMetadata;
import com.android.tools.r8.metadata.R8BuildMetadata;
import com.android.tools.r8.metadata.R8CompilationMetadata;
import com.android.tools.r8.metadata.R8DexFileMetadata;
import com.android.tools.r8.metadata.R8FeatureSplitsMetadata;
import com.android.tools.r8.metadata.R8OptionsMetadata;
import com.android.tools.r8.metadata.R8ResourceOptimizationMetadata;
import com.android.tools.r8.metadata.R8StartupOptimizationMetadata;
import com.android.tools.r8.metadata.R8StatsMetadata;
import com.android.tools.r8.utils.ListUtils;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.function.Consumer;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8BuildMetadataImpl implements R8BuildMetadata {

  @Expose
  @SerializedName("options")
  private final R8OptionsMetadata optionsMetadata;

  @Expose
  @SerializedName("baselineProfileRewriting")
  private final R8BaselineProfileRewritingMetadata baselineProfileRewritingMetadata;

  @Expose
  @SerializedName("compilation")
  private final R8CompilationMetadata compilationMetadata;

  @Expose
  @SerializedName("dexFiles")
  private final List<R8DexFileMetadata> dexFilesMetadata;

  @Expose
  @SerializedName("stats")
  private final R8StatsMetadata statsMetadata;

  @Expose
  @SerializedName("featureSplits")
  private final R8FeatureSplitsMetadata featureSplitsMetadata;

  @Expose
  @SerializedName("resourceOptimization")
  private final R8ResourceOptimizationMetadata resourceOptimizationMetadata;

  @Expose
  @SerializedName("startupOptimization")
  private final R8StartupOptimizationMetadata startupOptimizationMetadata;

  @Expose
  @SerializedName("version")
  private final String version;

  public R8BuildMetadataImpl(
      R8OptionsMetadata options,
      R8BaselineProfileRewritingMetadata baselineProfileRewritingOptions,
      R8CompilationMetadata compilationMetadata,
      List<R8DexFileMetadata> dexFilesMetadata,
      R8StatsMetadata statsMetadata,
      R8FeatureSplitsMetadata featureSplitsMetadata,
      R8ResourceOptimizationMetadata resourceOptimizationMetadata,
      R8StartupOptimizationMetadata startupOptimizationMetadata,
      String version) {
    this.optionsMetadata = options;
    this.baselineProfileRewritingMetadata = baselineProfileRewritingOptions;
    this.compilationMetadata = compilationMetadata;
    this.dexFilesMetadata = dexFilesMetadata;
    this.statsMetadata = statsMetadata;
    this.featureSplitsMetadata = featureSplitsMetadata;
    this.resourceOptimizationMetadata = resourceOptimizationMetadata;
    this.startupOptimizationMetadata = startupOptimizationMetadata;
    this.version = version;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public R8OptionsMetadata getOptionsMetadata() {
    return optionsMetadata;
  }

  @Override
  public R8BaselineProfileRewritingMetadata getBaselineProfileRewritingMetadata() {
    return baselineProfileRewritingMetadata;
  }

  @Override
  public R8CompilationMetadata getCompilationMetadata() {
    return compilationMetadata;
  }

  @Override
  public List<R8DexFileMetadata> getDexFilesMetadata() {
    return dexFilesMetadata;
  }

  @Override
  public R8FeatureSplitsMetadata getFeatureSplitsMetadata() {
    return featureSplitsMetadata;
  }

  @Override
  public R8ResourceOptimizationMetadata getResourceOptimizationMetadata() {
    return resourceOptimizationMetadata;
  }

  @Override
  public R8StartupOptimizationMetadata getStartupOptizationOptions() {
    return startupOptimizationMetadata;
  }

  @Override
  public R8StatsMetadata getStatsMetadata() {
    return statsMetadata;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public String toJson() {
    return new Gson().toJson(this);
  }

  public static class Builder {

    private R8OptionsMetadata options;
    private R8BaselineProfileRewritingMetadata baselineProfileRewritingOptions;
    private R8CompilationMetadata compilationInfo;
    private List<R8DexFileMetadata> dexFilesMetadata;
    private R8StatsMetadata statsMetadata;
    private R8FeatureSplitsMetadata featureSplitsMetadata;
    private R8ResourceOptimizationMetadata resourceOptimizationOptions;
    private R8StartupOptimizationMetadata startupOptimizationOptions;
    private String version;

    public Builder applyIf(boolean condition, Consumer<Builder> thenConsumer) {
      if (condition) {
        thenConsumer.accept(this);
      }
      return this;
    }

    public Builder setOptions(R8OptionsMetadata options) {
      this.options = options;
      return this;
    }

    public Builder setBaselineProfileRewritingOptions(
        R8BaselineProfileRewritingMetadata baselineProfileRewritingOptions) {
      this.baselineProfileRewritingOptions = baselineProfileRewritingOptions;
      return this;
    }

    public Builder setCompilationInfo(R8CompilationMetadata compilationInfo) {
      this.compilationInfo = compilationInfo;
      return this;
    }

    public Builder setDexFilesMetadata(List<VirtualFile> virtualFiles) {
      this.dexFilesMetadata = ListUtils.map(virtualFiles, R8DexFileMetadataImpl::create);
      return this;
    }

    public Builder setStatsMetadata(R8StatsMetadata statsMetadata) {
      this.statsMetadata = statsMetadata;
      return this;
    }

    public Builder setFeatureSplitsMetadata(R8FeatureSplitsMetadata featureSplitsMetadata) {
      this.featureSplitsMetadata = featureSplitsMetadata;
      return this;
    }

    public Builder setResourceOptimizationOptions(
        R8ResourceOptimizationMetadata resourceOptimizationOptions) {
      this.resourceOptimizationOptions = resourceOptimizationOptions;
      return this;
    }

    public Builder setStartupOptimizationOptions(
        R8StartupOptimizationMetadata startupOptimizationOptions) {
      this.startupOptimizationOptions = startupOptimizationOptions;
      return this;
    }

    public Builder setVersion(String version) {
      this.version = version;
      return this;
    }

    public R8BuildMetadataImpl build() {
      return new R8BuildMetadataImpl(
          options,
          baselineProfileRewritingOptions,
          compilationInfo,
          dexFilesMetadata,
          statsMetadata,
          featureSplitsMetadata,
          resourceOptimizationOptions,
          startupOptimizationOptions,
          version);
    }
  }
}
