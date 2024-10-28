// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public interface R8OptionsMetadata {

  /**
   * @return null if API modeling is disabled.
   */
  R8ApiModelingMetadata getApiModelingMetadata();

  /**
   * @return null if no ProGuard configuration is provided.
   */
  R8KeepAttributesMetadata getKeepAttributesMetadata();

  /**
   * @return null if library desugaring is disabled.
   */
  R8LibraryDesugaringMetadata getLibraryDesugaringMetadata();

  String getMinApiLevel();

  boolean hasObfuscationDictionary();

  boolean hasClassObfuscationDictionary();

  boolean hasPackageObfuscationDictionary();

  boolean isAccessModificationEnabled();

  boolean isDebugModeEnabled();

  boolean isFlattenPackageHierarchyEnabled();

  boolean isObfuscationEnabled();

  boolean isOptimizationsEnabled();

  boolean isProGuardCompatibilityModeEnabled();

  boolean isProtoLiteOptimizationEnabled();

  boolean isRepackageClassesEnabled();

  boolean isShrinkingEnabled();
}
