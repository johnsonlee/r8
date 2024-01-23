// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;

public abstract class ExtraUnusedPrimitiveParameter extends ExtraUnusedParameter {

  public static ExtraUnusedPrimitiveParameter create(DexType type) {
    switch (type.toDescriptorString().charAt(0)) {
      case 'B':
        return new ExtraUnusedByteParameter();
      case 'C':
        return new ExtraUnusedCharParameter();
      case 'I':
        return new ExtraUnusedIntParameter();
      case 'S':
        return new ExtraUnusedShortParameter();
      case 'Z':
        return new ExtraUnusedBooleanParameter();
      default:
        throw new Unreachable();
    }
  }

  @Override
  public SingleNumberValue getValue(AppView<?> appView) {
    return appView.abstractValueFactory().createZeroValue();
  }
}
