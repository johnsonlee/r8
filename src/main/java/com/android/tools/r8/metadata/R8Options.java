// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public interface R8Options {

  /**
   * @return null if API modeling is disabled.
   */
  R8ApiModelingOptions getApiModelingOptions();

  /**
   * @return null if no ProGuard configuration is provided.
   */
  R8KeepAttributesOptions getKeepAttributesOptions();

  /**
   * @return null if library desugaring is disabled.
   */
  R8LibraryDesugaringOptions getLibraryDesugaringOptions();

  int getMinApiLevel();

  boolean isAccessModificationEnabled();

  boolean isDebugModeEnabled();

  boolean isProGuardCompatibilityModeEnabled();

  boolean isObfuscationEnabled();

  boolean isOptimizationsEnabled();

  boolean isShrinkingEnabled();
}
