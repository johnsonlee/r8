// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8DexFileMetadata;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8DexFileMetadataImpl implements R8DexFileMetadata {

  @Expose
  @SerializedName("checksum")
  private final String checksum;

  @Expose
  @SerializedName("startup")
  private final boolean startup;

  private R8DexFileMetadataImpl(String checksum, boolean startup) {
    this.checksum = checksum;
    this.startup = startup;
  }

  public static R8DexFileMetadataImpl create(VirtualFile virtualFile) {
    assert !virtualFile.isEmpty();
    String checksum = virtualFile.getChecksumForBuildMetadata().toString();
    boolean startup = virtualFile.isStartup();
    return new R8DexFileMetadataImpl(checksum, startup);
  }

  @Override
  public String getChecksum() {
    return checksum;
  }

  @Override
  public boolean isStartup() {
    return startup;
  }
}
