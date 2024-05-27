// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.api.genericsignature;

import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import java.util.function.Function;

// Explicitly keeping the public member signatures (and holder) can be used in place of KeepForApi.
@UsedByReflection(
    kind = KeepItemKind.CLASS_AND_MEMBERS,
    memberAccess = MemberAccessFlags.PUBLIC,
    constraintAdditions = KeepConstraint.GENERIC_SIGNATURE)
public class MyOtherValueBox<S> {

  private final S value;

  public MyOtherValueBox(S value) {
    this.value = value;
  }

  public <T> T run(Function<S, T> fn) {
    return fn.apply(value);
  }
}
