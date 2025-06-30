// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value.objectstate;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.BiConsumer;

public class KnownLengthArrayState extends ObjectState {

  private final int length;

  public KnownLengthArrayState(int length) {
    this.length = length;
  }

  @Override
  public void forEachAbstractFieldValue(BiConsumer<DexField, AbstractValue> consumer) {
    // Intentionally empty.
  }

  @Override
  public AbstractValue getAbstractFieldValue(DexField field) {
    return UnknownValue.getInstance();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean hasKnownArrayLength() {
    return true;
  }

  @Override
  public int getKnownArrayLength() {
    return length;
  }

  @Override
  public ObjectState rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof KnownLengthArrayState)) {
      return false;
    }
    KnownLengthArrayState other = (KnownLengthArrayState) obj;
    return length == other.length;
  }

  @Override
  public int hashCode() {
    return 31 * (31 + length) + getClass().hashCode();
  }

  @Override
  public String toString() {
    return "KnownLengthArrayState(" + length + ")";
  }
}
