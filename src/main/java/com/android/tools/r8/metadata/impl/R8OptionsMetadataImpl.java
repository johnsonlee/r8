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
import com.android.tools.r8.shaking.ProguardConfiguration;
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
  @SerializedName("hasObfuscationDictionary")
  private final boolean hasObfuscationDictionary;

  @Expose
  @SerializedName("hasClassObfuscationDictionary")
  private final boolean hasClassObfuscationDictionary;

  @Expose
  @SerializedName("hasPackageObfuscationDictionary")
  private final boolean hasPackageObfuscationDictionary;

  @Expose
  @SerializedName("keepAttributes")
  private final R8KeepAttributesMetadata keepAttributesMetadata;

  @Expose
  @SerializedName("isAccessModificationEnabled")
  private final boolean isAccessModificationEnabled;

  @Expose
  @SerializedName("isFlattenPackageHierarchyEnabled")
  private final boolean isFlattenPackageHierarchyEnabled;

  @Expose
  @SerializedName("isObfuscationEnabled")
  private final boolean isObfuscationEnabled;

  @Expose
  @SerializedName("isOptimizationsEnabled")
  private final boolean isOptimizationsEnabled;

  @Expose
  @SerializedName("isProGuardCompatibilityModeEnabled")
  private final boolean isProGuardCompatibilityModeEnabled;

  @Expose
  @SerializedName("isProtoLiteOptimizationEnabled")
  private final boolean isProtoLiteOptimizationEnabled;

  @Expose
  @SerializedName("isRepackageClassesEnabled")
  private final boolean isRepackageClassesEnabled;

  @Expose
  @SerializedName("isShrinkingEnabled")
  private final boolean isShrinkingEnabled;

  public R8OptionsMetadataImpl(InternalOptions options) {
    super(
        R8ApiModelingMetadataImpl.create(options),
        R8LibraryDesugaringMetadataImpl.create(options),
        options);
    ProguardConfiguration configuration = options.getProguardConfiguration();
    boolean hasConfiguration = configuration != null;
    this.hasObfuscationDictionary =
        hasConfiguration && !configuration.getObfuscationDictionary().isEmpty();
    this.hasClassObfuscationDictionary =
        hasConfiguration && !configuration.getClassObfuscationDictionary().isEmpty();
    this.hasPackageObfuscationDictionary =
        hasConfiguration && !configuration.getPackageObfuscationDictionary().isEmpty();
    this.keepAttributesMetadata =
        hasConfiguration
            ? new R8KeepAttributesMetadataImpl(configuration.getKeepAttributes())
            : null;
    this.isAccessModificationEnabled = options.isAccessModificationEnabled();
    this.isFlattenPackageHierarchyEnabled =
        hasConfiguration && configuration.getPackageObfuscationMode().isFlattenPackageHierarchy();
    this.isObfuscationEnabled = options.isMinifying();
    this.isOptimizationsEnabled = options.isOptimizing();
    this.isProGuardCompatibilityModeEnabled = options.forceProguardCompatibility;
    this.isProtoLiteOptimizationEnabled = options.protoShrinking().isProtoShrinkingEnabled();
    this.isRepackageClassesEnabled =
        options.isRepackagingEnabled()
            && configuration.getPackageObfuscationMode().isRepackageClasses();
    this.isShrinkingEnabled = options.isShrinking();
  }

  @Override
  public R8KeepAttributesMetadata getKeepAttributesMetadata() {
    return keepAttributesMetadata;
  }

  @Override
  public boolean hasObfuscationDictionary() {
    return hasObfuscationDictionary;
  }

  @Override
  public boolean hasClassObfuscationDictionary() {
    return hasClassObfuscationDictionary;
  }

  @Override
  public boolean hasPackageObfuscationDictionary() {
    return hasPackageObfuscationDictionary;
  }

  @Override
  public boolean isAccessModificationEnabled() {
    return isAccessModificationEnabled;
  }

  @Override
  public boolean isFlattenPackageHierarchyEnabled() {
    return isFlattenPackageHierarchyEnabled;
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
  public boolean isProGuardCompatibilityModeEnabled() {
    return isProGuardCompatibilityModeEnabled;
  }

  @Override
  public boolean isProtoLiteOptimizationEnabled() {
    return isProtoLiteOptimizationEnabled;
  }

  @Override
  public boolean isRepackageClassesEnabled() {
    return isRepackageClassesEnabled;
  }

  @Override
  public boolean isShrinkingEnabled() {
    return isShrinkingEnabled;
  }
}
