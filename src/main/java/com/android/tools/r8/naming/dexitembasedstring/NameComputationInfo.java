// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.dexitembasedstring;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;

public abstract class NameComputationInfo<T extends DexReference> {

  enum Order {
    CLASSNAME,
    FIELDNAME,
    RECORD_MATCH,
    RECORD_MISMATCH
  }

  public final DexString computeNameFor(DexReference reference, AppView<?> appView) {
    return computeNameFor(reference, appView, appView.getNamingLens());
  }

  private DexString computeNameFor(
      DexReference reference, DexDefinitionSupplier definitions, NamingLens namingLens) {
    if (needsToComputeName()) {
      if (isFieldNameComputationInfo()) {
        return asFieldNameComputationInfo()
            .internalComputeNameFor(reference.asDexField(), definitions, namingLens);
      }
      if (isClassNameComputationInfo()) {
        return asClassNameComputationInfo()
            .internalComputeNameFor(reference.asDexType(), definitions, namingLens);
      }
      if (isRecordFieldNamesComputationInfo()) {
        return asRecordFieldNamesComputationInfo()
            .internalComputeNameFor(reference.asDexType(), definitions, namingLens);
      }
    }
    return namingLens.lookupName(reference, definitions.dexItemFactory());
  }

  public abstract DexString internalComputeNameFor(
      T reference, DexDefinitionSupplier definitions, NamingLens namingLens);

  abstract Order getOrder();

  public int acceptCompareTo(NameComputationInfo<?> other, CompareToVisitor visitor) {
    int diff = visitor.visitInt(getOrder().ordinal(), other.getOrder().ordinal());
    if (diff != 0) {
      return diff;
    }
    return internalAcceptCompareTo(other, visitor);
  }

  public void acceptHashing(HashingVisitor visitor) {
    visitor.visitInt(getOrder().ordinal());
    internalAcceptHashing(visitor);
  }

  abstract int internalAcceptCompareTo(NameComputationInfo<?> other, CompareToVisitor visitor);

  abstract void internalAcceptHashing(HashingVisitor visitor);

  public abstract boolean needsToComputeName();

  public abstract boolean needsToRegisterReference();

  public boolean isFieldNameComputationInfo() {
    return false;
  }

  public FieldNameComputationInfo asFieldNameComputationInfo() {
    return null;
  }

  public boolean isClassNameComputationInfo() {
    return false;
  }

  public ClassNameComputationInfo asClassNameComputationInfo() {
    return null;
  }

  public boolean isRecordFieldNamesComputationInfo() {
    return false;
  }

  public RecordFieldNamesComputationInfo asRecordFieldNamesComputationInfo() {
    return null;
  }

  public abstract NameComputationInfo<T> rewrittenWithLens(GraphLens graphLens, GraphLens codeLens);
}
