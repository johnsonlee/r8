// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8ApiModelingMetadata;
import com.android.tools.r8.metadata.R8KeepAttributesMetadata;
import com.android.tools.r8.metadata.R8LibraryDesugaringMetadata;
import com.android.tools.r8.metadata.R8OptionsMetadata;
import com.android.tools.r8.utils.InternalOptions;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8OptionsMetadataImpl
    extends D8R8OptionsMetadataImpl<R8ApiModelingMetadata, R8LibraryDesugaringMetadata>
    implements R8OptionsMetadata {

  @Expose
  @SerializedName("keepAttributes")
  private final R8KeepAttributesMetadata keepAttributesMetadata;

  @Expose
  @SerializedName("isAccessModificationEnabled")
  private final boolean isAccessModificationEnabled;

  @Expose
  @SerializedName("isProGuardCompatibilityModeEnabled")
  private final boolean isProGuardCompatibilityModeEnabled;

  @Expose
  @SerializedName("isObfuscationEnabled")
  private final boolean isObfuscationEnabled;

  @Expose
  @SerializedName("isOptimizationsEnabled")
  private final boolean isOptimizationsEnabled;

  @Expose
  @SerializedName("isShrinkingEnabled")
  private final boolean isShrinkingEnabled;

  public R8OptionsMetadataImpl(InternalOptions options) {
    super(
        R8ApiModelingMetadataImpl.create(options),
        R8LibraryDesugaringMetadataImpl.create(options),
        options);
    this.keepAttributesMetadata =
        options.hasProguardConfiguration()
            ? new R8KeepAttributesMetadataImpl(
                options.getProguardConfiguration().getKeepAttributes())
            : null;
    this.isAccessModificationEnabled = options.isAccessModificationEnabled();
    this.isProGuardCompatibilityModeEnabled = options.forceProguardCompatibility;
    this.isObfuscationEnabled = options.isMinifying();
    this.isOptimizationsEnabled = options.isOptimizing();
    this.isShrinkingEnabled = options.isShrinking();
  }

  @Override
  public R8KeepAttributesMetadata getKeepAttributesMetadata() {
    return keepAttributesMetadata;
  }

  @Override
  public boolean isAccessModificationEnabled() {
    return isAccessModificationEnabled;
  }

  @Override
  public boolean isProGuardCompatibilityModeEnabled() {
    return isProGuardCompatibilityModeEnabled;
  }

  @Override
  public boolean isObfuscationEnabled() {
    return isObfuscationEnabled;
  }

  @Override
  public boolean isOptimizationsEnabled() {
    return isOptimizationsEnabled;
  }

  @Override
  public boolean isShrinkingEnabled() {
    return isShrinkingEnabled;
  }
}
