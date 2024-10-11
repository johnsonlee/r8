// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

public interface D8R8OptionsMetadata<
    ApiModelingMetadata, LibraryDesugaringMetadata extends D8R8LibraryDesugaringMetadata> {

  /**
   * @return null if API modeling is disabled.
   */
  ApiModelingMetadata getApiModelingMetadata();

  /**
   * @return null if library desugaring is disabled.
   */
  LibraryDesugaringMetadata getLibraryDesugaringMetadata();

  int getMinApiLevel();

  boolean isDebugModeEnabled();
}
