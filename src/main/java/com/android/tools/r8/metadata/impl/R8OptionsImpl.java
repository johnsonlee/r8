// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8ApiModelingOptions;
import com.android.tools.r8.metadata.R8KeepAttributesOptions;
import com.android.tools.r8.metadata.R8LibraryDesugaringOptions;
import com.android.tools.r8.metadata.R8Options;
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
public class R8OptionsImpl extends D8R8OptionsImpl<R8ApiModelingOptions, R8LibraryDesugaringOptions>
    implements R8Options {

  @Expose
  @SerializedName("keepAttributesOptions")
  private final R8KeepAttributesOptions keepAttributesOptions;

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

  public R8OptionsImpl(InternalOptions options) {
    super(
        R8ApiModelingOptionsImpl.create(options),
        R8LibraryDesugaringOptionsImpl.create(options),
        options);
    this.keepAttributesOptions =
        options.hasProguardConfiguration()
            ? new R8KeepAttributesOptionsImpl(
                options.getProguardConfiguration().getKeepAttributes())
            : null;
    this.isAccessModificationEnabled = options.isAccessModificationEnabled();
    this.isProGuardCompatibilityModeEnabled = options.forceProguardCompatibility;
    this.isObfuscationEnabled = options.isMinifying();
    this.isOptimizationsEnabled = options.isOptimizing();
    this.isShrinkingEnabled = options.isShrinking();
  }

  @Override
  public R8KeepAttributesOptions getKeepAttributesOptions() {
    return keepAttributesOptions;
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
