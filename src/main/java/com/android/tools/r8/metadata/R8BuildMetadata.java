// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.metadata.impl.R8ApiModelingMetadataImpl;
import com.android.tools.r8.metadata.impl.R8BaselineProfileRewritingMetadataImpl;
import com.android.tools.r8.metadata.impl.R8BuildMetadataImpl;
import com.android.tools.r8.metadata.impl.R8CompilationMetadataImpl;
import com.android.tools.r8.metadata.impl.R8DexFileMetadataImpl;
import com.android.tools.r8.metadata.impl.R8FeatureSplitMetadataImpl;
import com.android.tools.r8.metadata.impl.R8FeatureSplitsMetadataImpl;
import com.android.tools.r8.metadata.impl.R8KeepAttributesMetadataImpl;
import com.android.tools.r8.metadata.impl.R8LibraryDesugaringMetadataImpl;
import com.android.tools.r8.metadata.impl.R8OptionsMetadataImpl;
import com.android.tools.r8.metadata.impl.R8ResourceOptimizationMetadataImpl;
import com.android.tools.r8.metadata.impl.R8StartupOptimizationMetadataImpl;
import com.android.tools.r8.metadata.impl.R8StatsMetadataImpl;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import java.util.List;

@KeepForApi
public interface R8BuildMetadata {

  static R8BuildMetadata fromJson(String json) {
    return new GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .registerTypeAdapter(R8OptionsMetadata.class, deserializeTo(R8OptionsMetadataImpl.class))
        .registerTypeAdapter(
            R8ApiModelingMetadata.class, deserializeTo(R8ApiModelingMetadataImpl.class))
        .registerTypeAdapter(
            R8BaselineProfileRewritingMetadata.class,
            deserializeTo(R8BaselineProfileRewritingMetadataImpl.class))
        .registerTypeAdapter(
            R8CompilationMetadata.class, deserializeTo(R8CompilationMetadataImpl.class))
        .registerTypeAdapter(R8DexFileMetadata.class, deserializeTo(R8DexFileMetadataImpl.class))
        .registerTypeAdapter(R8StatsMetadata.class, deserializeTo(R8StatsMetadataImpl.class))
        .registerTypeAdapter(
            R8FeatureSplitMetadata.class, deserializeTo(R8FeatureSplitMetadataImpl.class))
        .registerTypeAdapter(
            R8FeatureSplitsMetadata.class, deserializeTo(R8FeatureSplitsMetadataImpl.class))
        .registerTypeAdapter(
            R8KeepAttributesMetadata.class, deserializeTo(R8KeepAttributesMetadataImpl.class))
        .registerTypeAdapter(
            R8LibraryDesugaringMetadata.class, deserializeTo(R8LibraryDesugaringMetadataImpl.class))
        .registerTypeAdapter(
            R8ResourceOptimizationMetadata.class,
            deserializeTo(R8ResourceOptimizationMetadataImpl.class))
        .registerTypeAdapter(
            R8StartupOptimizationMetadata.class,
            deserializeTo(R8StartupOptimizationMetadataImpl.class))
        .create()
        .fromJson(json, R8BuildMetadataImpl.class);
  }

  private static <T> JsonDeserializer<T> deserializeTo(Class<T> implClass) {
    return (element, type, context) -> context.deserialize(element, implClass);
  }

  R8OptionsMetadata getOptionsMetadata();

  /**
   * @return null if baseline profile rewriting is disabled.
   */
  R8BaselineProfileRewritingMetadata getBaselineProfileRewritingMetadata();

  R8CompilationMetadata getCompilationMetadata();

  /**
   * @return null if not compiling to dex.
   */
  List<R8DexFileMetadata> getDexFilesMetadata();

  /**
   * @return null if not using feature splits.
   */
  R8FeatureSplitsMetadata getFeatureSplitsMetadata();

  /**
   * @return null if resource optimization is disabled.
   */
  R8ResourceOptimizationMetadata getResourceOptimizationMetadata();

  /**
   * @return null if startup optimization is disabled.
   */
  R8StartupOptimizationMetadata getStartupOptizationOptions();

  R8StatsMetadata getStatsMetadata();

  String getVersion();

  String toJson();
}
