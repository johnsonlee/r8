// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.metadata.impl.D8ApiModelingMetadataImpl;
import com.android.tools.r8.metadata.impl.D8BuildMetadataImpl;
import com.android.tools.r8.metadata.impl.D8LibraryDesugaringMetadataImpl;
import com.android.tools.r8.metadata.impl.D8OptionsMetadataImpl;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

@KeepForApi
public interface D8BuildMetadata {

  static D8BuildMetadata fromJson(String json) {
    return new GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .registerTypeAdapter(D8OptionsMetadata.class, deserializeTo(D8OptionsMetadataImpl.class))
        .registerTypeAdapter(
            D8ApiModelingMetadata.class, deserializeTo(D8ApiModelingMetadataImpl.class))
        .registerTypeAdapter(
            D8LibraryDesugaringMetadata.class, deserializeTo(D8LibraryDesugaringMetadataImpl.class))
        .create()
        .fromJson(json, D8BuildMetadataImpl.class);
  }

  private static <T> JsonDeserializer<T> deserializeTo(Class<T> implClass) {
    return (element, type, context) -> context.deserialize(element, implClass);
  }

  D8OptionsMetadata getOptions();

  String getVersion();

  String toJson();
}
