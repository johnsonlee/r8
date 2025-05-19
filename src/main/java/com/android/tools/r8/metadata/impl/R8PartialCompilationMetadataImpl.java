// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8PartialCompilationMetadata;
import com.android.tools.r8.metadata.R8PartialCompilationStatsMetadata;
import com.android.tools.r8.partial.R8PartialCompilationConfiguration;
import com.android.tools.r8.partial.predicate.R8PartialPredicate;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8PartialCompilationMetadataImpl implements R8PartialCompilationMetadata {

  @Expose
  @SerializedName("commonIncludePatterns")
  private final List<String> commonIncludePatterns;

  @Expose
  @SerializedName("numberOfExcludePatterns")
  private final int numberOfExcludePatterns;

  @Expose
  @SerializedName("numberOfIncludePatterns")
  private final int numberOfIncludePatterns;

  @Expose
  @SerializedName("stats")
  private final R8PartialCompilationStatsMetadata statsMetadata;

  private R8PartialCompilationMetadataImpl(
      List<String> commonIncludePatterns,
      int numberOfExcludePatterns,
      int numberOfIncludePatterns,
      R8PartialCompilationStatsMetadata statsMetadata) {
    this.commonIncludePatterns = commonIncludePatterns;
    this.numberOfExcludePatterns = numberOfExcludePatterns;
    this.numberOfIncludePatterns = numberOfIncludePatterns;
    this.statsMetadata = statsMetadata;
  }

  public static R8PartialCompilationMetadataImpl create(InternalOptions options) {
    if (options.partialCompilationConfiguration.isEnabled()) {
      return new R8PartialCompilationMetadataImpl(
          createCommonIncludePatterns(options.partialCompilationConfiguration),
          options.partialCompilationConfiguration.getExcludePredicates().size(),
          options.partialCompilationConfiguration.getIncludePredicates().size(),
          options.getR8PartialR8SubCompilationOptions().getStatsMetadataBuilder().build());
    }
    return null;
  }

  private static List<String> createCommonIncludePatterns(
      R8PartialCompilationConfiguration partialCompilationConfiguration) {
    Set<String> commonIncludePatterns = ImmutableSet.of("androidx.**", "kotlin.**", "kotlinx.**");
    List<String> result = new ArrayList<>();
    for (R8PartialPredicate includePredicate :
        partialCompilationConfiguration.getIncludePredicates()) {
      String includePattern = includePredicate.serializeToString();
      if (commonIncludePatterns.contains(includePattern)) {
        result.add(includePattern);
      }
    }
    return ListUtils.sort(result, String::compareTo);
  }

  @Override
  public List<String> getCommonIncludePatterns() {
    return commonIncludePatterns;
  }

  @Override
  public int getNumberOfExcludePatterns() {
    return numberOfExcludePatterns;
  }

  @Override
  public int getNumberOfIncludePatterns() {
    return numberOfIncludePatterns;
  }

  @Override
  public R8PartialCompilationStatsMetadata getStatsMetadata() {
    return statsMetadata;
  }
}
