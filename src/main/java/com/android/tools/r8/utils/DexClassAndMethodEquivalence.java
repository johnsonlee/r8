// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.google.common.base.Equivalence;

public class DexClassAndMethodEquivalence extends Equivalence<DexClassAndMethod> {

  private static final DexClassAndMethodEquivalence INSTANCE = new DexClassAndMethodEquivalence();

  private DexClassAndMethodEquivalence() {}

  public static DexClassAndMethodEquivalence get() {
    return INSTANCE;
  }

  @Override
  protected boolean doEquivalent(DexClassAndMethod method, DexClassAndMethod other) {
    return method.getDefinition() == other.getDefinition();
  }

  @Override
  protected int doHash(DexClassAndMethod method) {
    return method.getReference().hashCode();
  }
}
