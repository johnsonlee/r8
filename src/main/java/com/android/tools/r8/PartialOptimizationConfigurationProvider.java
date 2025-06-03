// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/** API for providing partial optimization configuration to the compiler. */
@KeepForApi
public interface PartialOptimizationConfigurationProvider {

  void getPartialOptimizationConfiguration(PartialOptimizationConfigurationBuilder builder);
}
