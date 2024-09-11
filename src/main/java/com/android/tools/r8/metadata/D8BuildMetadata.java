// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.google.gson.GsonBuilder;

@KeepForApi
public interface D8BuildMetadata {

  static D8BuildMetadata fromJson(String json) {
    return new GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create()
        .fromJson(json, D8BuildMetadataImpl.class);
  }

  String getVersion();

  String toJson();
}
