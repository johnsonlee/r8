// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ExtraUnusedParameter extends ExtraParameter {

  @SuppressWarnings("MixedMutabilityReturnType")
  public static List<ExtraUnusedParameter> computeExtraUnusedParameters(
      DexMethod from, DexMethod to) {
    assert to.getArity() >= from.getArity();
    int numberOfUnusedParameters = to.getArity() - from.getArity();
    if (numberOfUnusedParameters == 0) {
      return Collections.emptyList();
    }
    List<ExtraUnusedParameter> extraUnusedParameters = new ArrayList<>(numberOfUnusedParameters);
    for (int extraUnusedParameterIndex = from.getArity();
        extraUnusedParameterIndex < to.getParameters().size();
        extraUnusedParameterIndex++) {
      extraUnusedParameters.add(create(to.getParameter(extraUnusedParameterIndex)));
    }
    return extraUnusedParameters;
  }

  public static ExtraUnusedParameter create(DexType type) {
    if (type.isPrimitiveType()) {
      return ExtraUnusedPrimitiveParameter.create(type);
    } else {
      assert type.isReferenceType();
      return new ExtraUnusedNullParameter(type);
    }
  }

  @Override
  public final boolean isUnused() {
    return true;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public final boolean equals(Object obj) {
    return obj != null && getClass() == obj.getClass();
  }

  @Override
  public final int hashCode() {
    return getClass().hashCode();
  }
}
