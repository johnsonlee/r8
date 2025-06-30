// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.MaterializingInstructionsInfo;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ResourceConstNumber;
import com.android.tools.r8.ir.code.ValueFactory;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class SingleResourceNumberValue extends SingleConstValue {

  private final int value;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleResourceNumberValue(int value) {
    this.value = value;
  }

  @Override
  public boolean hasSingleMaterializingInstruction() {
    return true;
  }

  @Override
  public boolean isSingleResourceNumberValue() {
    return true;
  }

  @Override
  public SingleResourceNumberValue asSingleResourceNumberValue() {
    return this;
  }

  public int getValue() {
    return value;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SingleResourceNumberValue)) {
      return false;
    }
    SingleResourceNumberValue other = (SingleResourceNumberValue) obj;
    return value == other.value;
  }

  @Override
  public int hashCode() {
    return 31 * (31 + value) + getClass().hashCode();
  }

  @Override
  public String toString() {
    return "SingleResourceNumberValue(" + value + ")";
  }

  @Override
  public Instruction[] createMaterializingInstructions(
      AppView<?> appView,
      ProgramMethod context,
      ValueFactory valueFactory,
      MaterializingInstructionsInfo info) {
    ResourceConstNumber materializingInstruction =
        createMaterializingInstruction(appView, valueFactory, info);
    return new Instruction[] {materializingInstruction};
  }

  public ResourceConstNumber createMaterializingInstruction(
      AppView<?> appView, ValueFactory valueFactory, MaterializingInstructionsInfo info) {
    return createMaterializingInstruction(
        appView, valueFactory, info.getOutType(), info.getLocalInfo(), info.getPosition());
  }

  public ResourceConstNumber createMaterializingInstruction(
      AppView<?> appView,
      ValueFactory valueFactory,
      TypeElement type,
      DebugLocalInfo localInfo,
      Position position) {
    assert type.isInt();
    return ResourceConstNumber.builder()
        .setFreshOutValue(valueFactory, type, localInfo)
        .setPositionForNonThrowingInstruction(position, appView.options())
        .setValue(value)
        .build();
  }

  @Override
  boolean internalIsMaterializableInContext(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context) {
    return true;
  }

  @Override
  public boolean isMaterializableInAllContexts(AppView<AppInfoWithLiveness> appView) {
    return true;
  }

  @Override
  public InstanceFieldInitializationInfo fixupAfterParametersChanged(
      ArgumentInfoCollection argumentInfoCollection) {
    return this;
  }

  @Override
  public SingleValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens) {
    return this;
  }
}
