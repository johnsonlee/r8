// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.effectivelytrivialphioptimization.b345248270;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoAccessModification;

@NeverClassInline
class PackagePrivateImplementation implements I {
  @NoAccessModification static final I NULL = new PackagePrivateImplementation();
}
