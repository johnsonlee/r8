// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.position;

import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.utils.FieldReferenceUtils;

public class FieldPosition implements Position {

  private final FieldReference reference;

  public FieldPosition(FieldReference reference) {
    this.reference = reference;
  }

  @Override
  public String getDescription() {
    return toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FieldPosition)) {
      return false;
    }
    FieldPosition position = (FieldPosition) obj;
    return reference.equals(position.reference);
  }

  @Override
  public int hashCode() {
    return reference.hashCode();
  }

  @Override
  public String toString() {
    return FieldReferenceUtils.toSourceString(reference);
  }
}
