// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Objects;
import java.util.function.Consumer;

public class OriginalFieldWitness implements LirConstant, StructuralItem<OriginalFieldWitness> {
  private final OriginalFieldWitness parent;
  private final DexField originalField;

  private static void specify(StructuralSpecification<OriginalFieldWitness, ?> spec) {
    spec.withItem(d -> d.originalField).withNullableItem(d -> d.parent);
  }

  private OriginalFieldWitness(OriginalFieldWitness parent, DexField originalField) {
    this.parent = parent;
    this.originalField = originalField;
  }

  @Override
  public OriginalFieldWitness self() {
    return this;
  }

  @Override
  public StructuralMapping<OriginalFieldWitness> getStructuralMapping() {
    return OriginalFieldWitness::specify;
  }

  public static OriginalFieldWitness forProgramField(ProgramField field) {
    return new OriginalFieldWitness(null, field.getReference());
  }

  public boolean isEqualToDexField(DexField field) {
    return parent == null && originalField.isIdenticalTo(field);
  }

  public void forEachReference(Consumer<? super DexField> fn) {
    fn.accept(originalField);
    if (parent != null) {
      parent.forEachReference(fn);
    }
  }

  @Override
  public String toString() {
    if (parent == null) {
      return originalField.toString();
    }
    return originalField + ":" + parent.toString();
  }

  @Override
  public boolean equals(Object obj) {
    return Equatable.equalsImpl(this, obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(originalField, parent);
  }

  @Override
  public LirConstantOrder getLirConstantOrder() {
    return LirConstantOrder.ORIGINAL_FIELD_WITNESS;
  }

  @Override
  public int internalLirConstantAcceptCompareTo(LirConstant other, CompareToVisitor visitor) {
    return visitor.visit(this, (OriginalFieldWitness) other, OriginalFieldWitness::specify);
  }

  @Override
  public void internalLirConstantAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, OriginalFieldWitness::specify);
  }
}
