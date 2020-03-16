// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;
import java.util.Map;

public class FinalInitClassLens extends InitClassLens {

  private final Map<DexType, DexField> mapping;

  FinalInitClassLens(Map<DexType, DexField> mapping) {
    this.mapping = mapping;
  }

  @Override
  public DexField getInitClassField(DexType type) {
    DexField field = mapping.get(type);
    if (field != null) {
      return field;
    }
    throw new Unreachable("Unexpected InitClass instruction for `" + type.toSourceString() + "`");
  }

  @Override
  public boolean isFinal() {
    return true;
  }
}
