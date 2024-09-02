// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.ResourceShrinkerConfiguration;
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
public class R8ResourceOptimizationOptionsImpl implements R8ResourceOptimizationOptions {

  @Expose
  @SerializedName("isOptimizedShrinkingEnabled")
  private final boolean isOptimizedShrinkingEnabled;

  private R8ResourceOptimizationOptionsImpl(
      ResourceShrinkerConfiguration resourceShrinkerConfiguration) {
    this.isOptimizedShrinkingEnabled = resourceShrinkerConfiguration.isOptimizedShrinking();
  }

  public static R8ResourceOptimizationOptionsImpl create(InternalOptions options) {
    if (options.androidResourceProvider == null) {
      return null;
    }
    return new R8ResourceOptimizationOptionsImpl(options.resourceShrinkerConfiguration);
  }

  @Override
  public boolean isOptimizedShrinkingEnabled() {
    return isOptimizedShrinkingEnabled;
  }
}
