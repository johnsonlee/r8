// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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
      R8ResourceOptimizationOptions resourceOptimizationOptions,
      R8StartupOptimizationOptions startupOptimizationOptions,
      String version) {
    this.options = options;
    this.baselineProfileRewritingOptions = baselineProfileRewritingOptions;
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
  public R8ResourceOptimizationOptions getResourceOptimizationOptions() {
    return resourceOptimizationOptions;
  }

  @Override
  public R8StartupOptimizationOptions getStartupOptizationOptions() {
    return startupOptimizationOptions;
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
    private R8ResourceOptimizationOptions resourceOptimizationOptions;
    private R8StartupOptimizationOptions startupOptimizationOptions;
    private String version;

    public Builder setOptions(R8Options options) {
      this.options = options;
      return this;
    }

    public Builder setBaselineProfileRewritingOptions(
        R8BaselineProfileRewritingOptions baselineProfileRewritingOptions) {
      this.baselineProfileRewritingOptions = baselineProfileRewritingOptions;
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
          resourceOptimizationOptions,
          startupOptimizationOptions,
          version);
    }
  }
}
