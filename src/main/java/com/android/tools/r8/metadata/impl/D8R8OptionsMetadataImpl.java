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
abstract class D8R8OptionsMetadataImpl<
        ApiModelingMetadata, LibraryDesugaringMetadata extends D8R8LibraryDesugaringMetadata>
    implements D8R8OptionsMetadata<ApiModelingMetadata, LibraryDesugaringMetadata> {

  @Expose
  @SerializedName("apiModeling")
  private final ApiModelingMetadata apiModelingMetadata;

  @Expose
  @SerializedName("libraryDesugaring")
  private final LibraryDesugaringMetadata libraryDesugaringMetadata;

  @Expose
  @SerializedName("minApiLevel")
  private final String minApiLevel;

  @Expose
  @SerializedName("isDebugModeEnabled")
  private final boolean isDebugModeEnabled;

  public D8R8OptionsMetadataImpl(
      ApiModelingMetadata apiModelingMetadata,
      LibraryDesugaringMetadata libraryDesugaringMetadata,
      InternalOptions options) {
    this.apiModelingMetadata = apiModelingMetadata;
    this.libraryDesugaringMetadata = libraryDesugaringMetadata;
    this.minApiLevel =
        options.isGeneratingDex() ? Integer.toString(options.getMinApiLevel().getLevel()) : null;
    this.isDebugModeEnabled = options.debug;
  }

  @Override
  public ApiModelingMetadata getApiModelingMetadata() {
    return apiModelingMetadata;
  }

  @Override
  public LibraryDesugaringMetadata getLibraryDesugaringMetadata() {
    return libraryDesugaringMetadata;
  }

  @Override
  public String getMinApiLevel() {
    return minApiLevel;
  }

  @Override
  public boolean isDebugModeEnabled() {
    return isDebugModeEnabled;
  }
}
