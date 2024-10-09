// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8CompilationInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.concurrent.ExecutorService;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8CompilationInfoImpl implements R8CompilationInfo {

  @Expose
  @SerializedName("buildTime")
  private final long buildTime;

  @Expose
  @SerializedName("numberOfThreads")
  private final long numberOfThreads;

  private R8CompilationInfoImpl(long buildTime, int numberOfThreads) {
    this.buildTime = buildTime;
    this.numberOfThreads = numberOfThreads;
  }

  public static R8CompilationInfoImpl create(
      ExecutorService executorService, InternalOptions options) {
    assert options.created > 0;
    long buildTime = System.nanoTime() - options.created;
    return new R8CompilationInfoImpl(buildTime, ThreadUtils.getNumberOfThreads(executorService));
  }

  @Override
  public long getBuildTime() {
    return buildTime;
  }

  @Override
  public long getNumberOfThreads() {
    return numberOfThreads;
  }
}
