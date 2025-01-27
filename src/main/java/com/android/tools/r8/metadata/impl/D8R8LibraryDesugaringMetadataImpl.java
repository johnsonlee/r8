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
abstract class D8R8LibraryDesugaringMetadataImpl implements D8R8LibraryDesugaringMetadata {

  @Expose
  @SerializedName("identifier")
  private final String identifier;

  public D8R8LibraryDesugaringMetadataImpl(InternalOptions options) {
    this.identifier = options.getLibraryDesugaringOptions().getIdentifier();
  }

  @Override
  public String getIdentifier() {
    return identifier;
  }
}
