// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class KeepClassItemReference extends KeepItemReference {

  public static KeepClassItemReference fromBindingReference(
      KeepClassBindingReference bindingReference) {
    return new ClassBinding(bindingReference);
  }

  @Override
  public final KeepClassItemReference asClassItemReference() {
    return this;
  }

  public final <T> T applyClassItemReference(
      Function<KeepBindingReference, T> onBinding, Function<KeepClassItemPattern, T> onPattern) {
    if (isBindingReference()) {
      return onBinding.apply(asBindingReference());
    }
    return onPattern.apply(asClassItemPattern());
  }

  public final void matchClassItemReference(
      Consumer<KeepBindingReference> onBinding, Consumer<KeepClassItemPattern> onPattern) {
    applyClassItemReference(AstUtils.toVoidFunction(onBinding), AstUtils.toVoidFunction(onPattern));
  }

  public abstract Collection<KeepBindingReference> getBindingReferences();

  private static class ClassBinding extends KeepClassItemReference {
    private final KeepClassBindingReference bindingReference;

    private ClassBinding(KeepClassBindingReference bindingReference) {
      assert bindingReference != null;
      this.bindingReference = bindingReference;
    }

    @Override
    public KeepClassBindingReference asBindingReference() {
      return bindingReference;
    }

    @Override
    public Collection<KeepBindingReference> getBindingReferences() {
      return Collections.singletonList(bindingReference);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ClassBinding)) {
        return false;
      }
      ClassBinding that = (ClassBinding) o;
      return bindingReference.equals(that.bindingReference);
    }

    @Override
    public int hashCode() {
      return bindingReference.hashCode();
    }

    @Override
    public String toString() {
      return bindingReference.toString();
    }
  }
}
