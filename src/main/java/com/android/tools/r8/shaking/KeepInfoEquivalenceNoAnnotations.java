// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.google.common.base.Equivalence;

public class KeepInfoEquivalenceNoAnnotations<T extends KeepInfo<?, T>> extends Equivalence<T> {

  @Override
  protected boolean doEquivalent(T a, T b) {
    return a.equalsNoAnnotations(b);
  }

  @Override
  protected int doHash(T a) {
    return a.hashCodeNoAnnotations();
  }

  static class ClassEquivalenceNoAnnotations
      extends KeepInfoEquivalenceNoAnnotations<KeepClassInfo> {}

  static class MethodEquivalenceNoAnnotations
      extends KeepInfoEquivalenceNoAnnotations<KeepMethodInfo> {}

  static class FieldEquivalenceNoAnnotations
      extends KeepInfoEquivalenceNoAnnotations<KeepFieldInfo> {}
}
