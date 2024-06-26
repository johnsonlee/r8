// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.BindingReference;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class KeepBindingReference {

  public static KeepClassBindingReference forClass(KeepBindingSymbol name) {
    return new KeepClassBindingReference(name);
  }

  public static KeepMemberBindingReference forMember(KeepBindingSymbol name) {
    return new KeepMemberBindingReference(name);
  }

  public static KeepBindingReference forItem(KeepBindingSymbol name, KeepItemPattern item) {
    return item.isClassItemPattern() ? forClass(name) : forMember(name);
  }

  private final KeepBindingSymbol name;

  KeepBindingReference(KeepBindingSymbol name) {
    this.name = name;
  }

  public KeepBindingSymbol getName() {
    return name;
  }

  public final boolean isClassType() {
    return asClassBindingReference() != null;
  }

  public final boolean isMemberType() {
    return asMemberBindingReference() != null;
  }

  public KeepClassBindingReference asClassBindingReference() {
    return null;
  }

  public KeepMemberBindingReference asMemberBindingReference() {
    return null;
  }

  public final <T> T apply(
      Function<KeepClassBindingReference, T> onClass,
      Function<KeepMemberBindingReference, T> onMember) {
    if (isClassType()) {
      return onClass.apply(asClassBindingReference());
    }
    assert isMemberType();
    return onMember.apply(asMemberBindingReference());
  }

  public final void match(
      Consumer<KeepClassBindingReference> onClass, Consumer<KeepMemberBindingReference> onMember) {
    apply(AstUtils.toVoidFunction(onClass), AstUtils.toVoidFunction(onMember));
  }

  @Override
  public String toString() {
    return name.toString();
  }

  @Override
  public final boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof KeepBindingReference)) {
      return false;
    }
    KeepBindingReference other = (KeepBindingReference) obj;
    return isClassType() == other.isClassType() && name.equals(other.name);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(isClassType(), name);
  }

  public BindingReference.Builder buildProto() {
    return BindingReference.newBuilder().setName(name.toString());
  }
}
