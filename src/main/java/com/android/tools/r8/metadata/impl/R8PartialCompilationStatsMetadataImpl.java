// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8PartialCompilationStatsMetadata;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8PartialCompilationStatsMetadataImpl implements R8PartialCompilationStatsMetadata {

  @Expose
  @SerializedName("dexCodeSizeOfExcludedClassesInBytes")
  private final int dexCodeSizeOfExcludedClassesInBytes;

  @Expose
  @SerializedName("dexCodeSizeOfIncludedClassesInBytes")
  private final int dexCodeSizeOfIncludedClassesInBytes;

  @Expose
  @SerializedName("numberOfExcludedClassesInInput")
  private final int numberOfExcludedClassesInInput;

  @Expose
  @SerializedName("numberOfIncludedClassesInInput")
  private final int numberOfIncludedClassesInInput;

  @Expose
  @SerializedName("numberOfIncludedClassesInOutput")
  private final int numberOfIncludedClassesInOutput;

  R8PartialCompilationStatsMetadataImpl(
      int dexCodeSizeOfExcludedClassesInBytes,
      int dexCodeSizeOfIncludedClassesInBytes,
      int numberOfExcludedClassesInInput,
      int numberOfIncludedClassesInInput,
      int numberOfIncludedClassesInOutput) {
    this.dexCodeSizeOfExcludedClassesInBytes = dexCodeSizeOfExcludedClassesInBytes;
    this.dexCodeSizeOfIncludedClassesInBytes = dexCodeSizeOfIncludedClassesInBytes;
    this.numberOfExcludedClassesInInput = numberOfExcludedClassesInInput;
    this.numberOfIncludedClassesInInput = numberOfIncludedClassesInInput;
    this.numberOfIncludedClassesInOutput = numberOfIncludedClassesInOutput;
  }

  @Override
  public int getDexCodeSizeOfExcludedClassesInBytes() {
    return dexCodeSizeOfExcludedClassesInBytes;
  }

  @Override
  public int getDexCodeSizeOfIncludedClassesInBytes() {
    return dexCodeSizeOfIncludedClassesInBytes;
  }

  @Override
  public int getNumberOfExcludedClassesInInput() {
    return numberOfExcludedClassesInInput;
  }

  @Override
  public int getNumberOfIncludedClassesInInput() {
    return numberOfIncludedClassesInInput;
  }

  @Override
  public int getNumberOfIncludedClassesInOutput() {
    return numberOfIncludedClassesInOutput;
  }
}
