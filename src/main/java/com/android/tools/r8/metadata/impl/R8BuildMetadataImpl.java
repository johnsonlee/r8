// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8BaselineProfileRewritingOptions;
import com.android.tools.r8.metadata.R8BuildMetadata;
import com.android.tools.r8.metadata.R8CompilationInfo;
import com.android.tools.r8.metadata.R8DexFileMetadata;
import com.android.tools.r8.metadata.R8FeatureSplitsMetadata;
import com.android.tools.r8.metadata.R8Options;
import com.android.tools.r8.metadata.R8ResourceOptimizationOptions;
import com.android.tools.r8.metadata.R8StartupOptimizationOptions;
import com.android.tools.r8.metadata.R8StatsMetadata;
import com.android.tools.r8.utils.ListUtils;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
  private final R8Options options;

  @Expose
  @SerializedName("baselineProfileRewritingOptions")
  private final R8BaselineProfileRewritingOptions baselineProfileRewritingOptions;

  @Expose
  @SerializedName("compilationInfo")
  private final R8CompilationInfo compilationInfo;

  @Expose
  @SerializedName("dexFilesMetadata")
  private final List<R8DexFileMetadata> dexFilesMetadata;

  @Expose
  @SerializedName("statsMetadata")
  private final R8StatsMetadata statsMetadata;

  @Expose
  @SerializedName("featureSplitsMetadata")
  private final R8FeatureSplitsMetadata featureSplitsMetadata;

  @Expose
  @SerializedName("resourceOptimizationOptions")
  private final R8ResourceOptimizationOptions resourceOptimizationOptions;

  @Expose
  @SerializedName("startupOptimizationOptions")
  private final R8StartupOptimizationOptions startupOptimizationOptions;

  @Expose
  @SerializedName("version")
  private final String version;

  public R8BuildMetadataImpl(
      R8Options options,
      R8BaselineProfileRewritingOptions baselineProfileRewritingOptions,
      R8CompilationInfo compilationInfo,
      List<R8DexFileMetadata> dexFilesMetadata,
      R8StatsMetadata statsMetadata,
      R8FeatureSplitsMetadata featureSplitsMetadata,
      R8ResourceOptimizationOptions resourceOptimizationOptions,
      R8StartupOptimizationOptions startupOptimizationOptions,
      String version) {
    this.options = options;
    this.baselineProfileRewritingOptions = baselineProfileRewritingOptions;
    this.compilationInfo = compilationInfo;
    this.dexFilesMetadata = dexFilesMetadata;
    this.statsMetadata = statsMetadata;
    this.featureSplitsMetadata = featureSplitsMetadata;
    this.resourceOptimizationOptions = resourceOptimizationOptions;
    this.startupOptimizationOptions = startupOptimizationOptions;
    this.version = version;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public R8Options getOptions() {
    return options;
  }

  @Override
  public R8BaselineProfileRewritingOptions getBaselineProfileRewritingOptions() {
    return baselineProfileRewritingOptions;
  }

  @Override
  public R8CompilationInfo getCompilationInfo() {
    return compilationInfo;
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
  public R8ResourceOptimizationOptions getResourceOptimizationOptions() {
    return resourceOptimizationOptions;
  }

  @Override
  public R8StartupOptimizationOptions getStartupOptizationOptions() {
    return startupOptimizationOptions;
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

    private R8Options options;
    private R8BaselineProfileRewritingOptions baselineProfileRewritingOptions;
    private R8CompilationInfo compilationInfo;
    private List<R8DexFileMetadata> dexFilesMetadata;
    private R8StatsMetadata statsMetadata;
    private R8FeatureSplitsMetadata featureSplitsMetadata;
    private R8ResourceOptimizationOptions resourceOptimizationOptions;
    private R8StartupOptimizationOptions startupOptimizationOptions;
    private String version;

    public Builder applyIf(boolean condition, Consumer<Builder> thenConsumer) {
      if (condition) {
        thenConsumer.accept(this);
      }
      return this;
    }

    public Builder setOptions(R8Options options) {
      this.options = options;
      return this;
    }

    public Builder setBaselineProfileRewritingOptions(
        R8BaselineProfileRewritingOptions baselineProfileRewritingOptions) {
      this.baselineProfileRewritingOptions = baselineProfileRewritingOptions;
      return this;
    }

    public Builder setCompilationInfo(R8CompilationInfo compilationInfo) {
      this.compilationInfo = compilationInfo;
      return this;
    }

    public Builder setDexFilesMetadata(List<VirtualFile> virtualFiles) {
      List<String> checksums =
          virtualFiles.stream()
              .filter(not(VirtualFile::isEmpty))
              .map(virtualFile -> virtualFile.getChecksumForBuildMetadata().toString())
              .collect(Collectors.toList());
      this.dexFilesMetadata = ListUtils.map(checksums, R8DexFileMetadataImpl::new);
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
        R8ResourceOptimizationOptions resourceOptimizationOptions) {
      this.resourceOptimizationOptions = resourceOptimizationOptions;
      return this;
    }

    public Builder setStartupOptimizationOptions(
        R8StartupOptimizationOptions startupOptimizationOptions) {
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
