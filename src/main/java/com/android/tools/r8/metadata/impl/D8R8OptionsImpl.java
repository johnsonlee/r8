// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

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
abstract class D8R8OptionsImpl<
        ApiModelingOptions, LibraryDesugaringOptions extends D8R8LibraryDesugaringOptions>
    implements D8R8Options<ApiModelingOptions, LibraryDesugaringOptions> {

  @Expose
  @SerializedName("apiModelingOptions")
  private final ApiModelingOptions apiModelingOptions;

  @Expose
  @SerializedName("libraryDesugaringOptions")
  private final LibraryDesugaringOptions libraryDesugaringOptions;

  @Expose
  @SerializedName("minApiLevel")
  private final int minApiLevel;

  @Expose
  @SerializedName("isDebugModeEnabled")
  private final boolean isDebugModeEnabled;

  public D8R8OptionsImpl(
      ApiModelingOptions apiModelingOptions,
      LibraryDesugaringOptions libraryDesugaringOptions,
      InternalOptions options) {
    this.apiModelingOptions = apiModelingOptions;
    this.libraryDesugaringOptions = libraryDesugaringOptions;
    this.minApiLevel = options.isGeneratingDex() ? options.getMinApiLevel().getLevel() : -1;
    this.isDebugModeEnabled = options.debug;
  }

  @Override
  public ApiModelingOptions getApiModelingOptions() {
    return apiModelingOptions;
  }

  @Override
  public LibraryDesugaringOptions getLibraryDesugaringOptions() {
    return libraryDesugaringOptions;
  }

  @Override
  public int getMinApiLevel() {
    return minApiLevel;
  }

  @Override
  public boolean isDebugModeEnabled() {
    return isDebugModeEnabled;
  }
}
