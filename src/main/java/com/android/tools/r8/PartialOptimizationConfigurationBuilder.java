// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.PackageReference;

/** API for building partial optimization configuration for the compiler. */
@KeepForApi
public interface PartialOptimizationConfigurationBuilder {

  /**
   * Add a class to be optimized with partial optimization. Note that this does *not* include inner
   * classes.
   *
   * @param classReference class to be optimized.
   * @return instance which received the call for chaining of calls.
   */
  PartialOptimizationConfigurationBuilder addClass(ClassReference classReference);

  /**
   * Add a complete package to be optimized with partial optimization. Note that this does *not*
   * include sub-packages.
   *
   * @param packageReference package to be optimized.
   * @return instance which received the call for chaining of calls.
   */
  PartialOptimizationConfigurationBuilder addPackage(PackageReference packageReference);
}
