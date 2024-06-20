// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

/**
 * A reference to an item pattern.
 *
 * <p>A reference is always a binding-reference and will be replaced soon.
 */
public abstract class KeepItemReference {

  public final boolean isClassItemReference() {
    return asClassItemReference() != null;
  }

  public KeepClassItemReference asClassItemReference() {
    return null;
  }

  public abstract KeepBindingReference asBindingReference();
}
