// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
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
public class R8OptionsImpl implements R8Options {

  @Expose
  @SerializedName("keepAttributesOptions")
  private final R8KeepAttributesOptions keepAttributesOptions;

  @Expose
  @SerializedName("minApiLevel")
  private final int minApiLevel;

  @Expose
  @SerializedName("isAccessModificationEnabled")
  private final boolean isAccessModificationEnabled;

  @Expose
  @SerializedName("isDebugModeEnabled")
  private final boolean isDebugModeEnabled;

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
    this.keepAttributesOptions =
        options.hasProguardConfiguration()
            ? new R8KeepAttributesOptionsImpl(
                options.getProguardConfiguration().getKeepAttributes())
            : null;
    this.minApiLevel = options.getMinApiLevel().getLevel();
    this.isAccessModificationEnabled = options.isAccessModificationEnabled();
    this.isDebugModeEnabled = options.debug;
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
  public int getMinApiLevel() {
    return minApiLevel;
  }

  @Override
  public boolean isAccessModificationEnabled() {
    return isAccessModificationEnabled;
  }

  @Override
  public boolean isDebugModeEnabled() {
    return isDebugModeEnabled;
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
