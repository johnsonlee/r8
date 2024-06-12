// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.effectivelytrivialphioptimization.b345248270;

public class PublicAccessor {
  public static I getPackagePrivateImplementation() {
    return PackagePrivateImplementation.NULL;
  }

  public static Class<?> getPackagePrivateImplementationClass() {
    return PackagePrivateImplementation.class;
  }
}
