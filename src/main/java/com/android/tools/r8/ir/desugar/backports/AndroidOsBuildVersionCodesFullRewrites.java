// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.StaticFieldReadRewriter;

public final class AndroidOsBuildVersionCodesFullRewrites {

  public static StaticFieldReadRewriter rewriteToConstInstruction(int versionCodeFull) {
    return (invoke, factory) -> new CfConstNumber(versionCodeFull, ValueType.INT);
  }

  private AndroidOsBuildVersionCodesFullRewrites() {}
}
