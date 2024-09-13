// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.metadata.impl.R8ApiModelingOptionsImpl;
import com.android.tools.r8.metadata.impl.R8BaselineProfileRewritingOptionsImpl;
import com.android.tools.r8.metadata.impl.R8BuildMetadataImpl;
import com.android.tools.r8.metadata.impl.R8KeepAttributesOptionsImpl;
import com.android.tools.r8.metadata.impl.R8LibraryDesugaringOptionsImpl;
import com.android.tools.r8.metadata.impl.R8OptionsImpl;
import com.android.tools.r8.metadata.impl.R8ResourceOptimizationOptionsImpl;
import com.android.tools.r8.metadata.impl.R8StartupOptimizationOptionsImpl;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import java.util.List;

@KeepForApi
public interface R8BuildMetadata {

  static R8BuildMetadata fromJson(String json) {
    return new GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .registerTypeAdapter(R8Options.class, deserializeTo(R8OptionsImpl.class))
        .registerTypeAdapter(
            R8ApiModelingOptions.class, deserializeTo(R8ApiModelingOptionsImpl.class))
        .registerTypeAdapter(
            R8BaselineProfileRewritingOptions.class,
            deserializeTo(R8BaselineProfileRewritingOptionsImpl.class))
        .registerTypeAdapter(
            R8KeepAttributesOptions.class, deserializeTo(R8KeepAttributesOptionsImpl.class))
        .registerTypeAdapter(
            R8LibraryDesugaringOptions.class, deserializeTo(R8LibraryDesugaringOptionsImpl.class))
        .registerTypeAdapter(
            R8ResourceOptimizationOptions.class,
            deserializeTo(R8ResourceOptimizationOptionsImpl.class))
        .registerTypeAdapter(
            R8StartupOptimizationOptions.class,
            deserializeTo(R8StartupOptimizationOptionsImpl.class))
        .create()
        .fromJson(json, R8BuildMetadataImpl.class);
  }

  private static <T> JsonDeserializer<T> deserializeTo(Class<T> implClass) {
    return (element, type, context) -> context.deserialize(element, implClass);
  }

  R8Options getOptions();

  /**
   * @return null if baseline profile rewriting is disabled.
   */
  R8BaselineProfileRewritingOptions getBaselineProfileRewritingOptions();

  /**
   * @return null if not compiling to dex.
   */
  List<String> getDexChecksums();

  /**
   * @return null if resource optimization is disabled.
   */
  R8ResourceOptimizationOptions getResourceOptimizationOptions();

  /**
   * @return null if startup optimization is disabled.
   */
  R8StartupOptimizationOptions getStartupOptizationOptions();

  String getVersion();

  String toJson();
}
