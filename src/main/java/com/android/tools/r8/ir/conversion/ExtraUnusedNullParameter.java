// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleNullValue;

public class ExtraUnusedNullParameter extends ExtraUnusedParameter {

  private final DexType type;

  public ExtraUnusedNullParameter(DexType type) {
    assert type.isReferenceType();
    this.type = type;
  }

  @Override
  public DexType getType(DexItemFactory dexItemFactory) {
    assert type != null;
    return type;
  }

  @Override
  public TypeElement getTypeElement(AppView<?> appView, DexType argType) {
    return TypeElement.fromDexType(argType, Nullability.maybeNull(), appView);
  }

  @Override
  public SingleNullValue getValue(AppView<?> appView) {
    return appView.abstractValueFactory().createNullValue(type);
  }
}
