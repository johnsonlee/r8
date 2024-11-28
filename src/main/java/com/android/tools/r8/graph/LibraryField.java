// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public class LibraryField extends DexClassAndField
    implements LibraryMember<DexEncodedField, DexField> {

  public LibraryField(DexLibraryClass holder, DexEncodedField field) {
    super(holder, field);
  }

  @Override
  public DexLibraryClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isLibraryClass();
    return holder.asLibraryClass();
  }

  @Override
  public boolean isLibraryField() {
    return true;
  }

  @Override
  public LibraryField asLibraryField() {
    return this;
  }

  @Override
  public boolean isLibraryMember() {
    return true;
  }

  @Override
  public boolean isFinalOrEffectivelyFinal(AppView<?> appView) {
    // We currently assume that fields declared final in the given android.jar are final at runtime.
    // If this turns out not to be the case for some fields, we can either add a deny list or extend
    // the API database.
    //
    // We explicitly do not treat System.in, System.out and System.err as final since they have
    // explicit setters, e.g., System#setIn.
    return getAccessFlags().isFinal()
        && getHolderType().isNotIdenticalTo(appView.dexItemFactory().javaLangSystemType);
  }
}
