// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.ItemPattern;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A pattern for matching items in the program.
 *
 * <p>An item pattern can describe a family of classes or a family of members.
 *
 * <p>A pattern cannot describe both a class *and* a member of a class. Either it is a pattern on
 * classes or it is a pattern on members.
 */
public abstract class KeepItemPattern {

  public static KeepClassItemPattern anyClass() {
    return KeepClassItemPattern.any();
  }

  public boolean isClassItemPattern() {
    return asClassItemPattern() != null;
  }

  public boolean isMemberItemPattern() {
    return asMemberItemPattern() != null;
  }

  public KeepClassItemPattern asClassItemPattern() {
    return null;
  }

  public KeepMemberItemPattern asMemberItemPattern() {
    return null;
  }

  public abstract Collection<KeepBindingReference> getBindingReferences();

  public final <T> T apply(
      Function<KeepClassItemPattern, T> onClass, Function<KeepMemberItemPattern, T> onMember) {
    if (isClassItemPattern()) {
      return onClass.apply(asClassItemPattern());
    }
    assert isMemberItemPattern();
    return onMember.apply(asMemberItemPattern());
  }

  public final void match(
      Consumer<KeepClassItemPattern> onClass, Consumer<KeepMemberItemPattern> onMember) {
    apply(AstUtils.toVoidFunction(onClass), AstUtils.toVoidFunction(onMember));
  }

  public static KeepItemPattern fromItemProto(
      ItemPattern proto,
      KeepItemPattern defaultValue,
      Function<String, KeepBindingSymbol> getSymbol) {
    if (proto.hasClassItem()) {
      return KeepClassItemPattern.fromClassProto(proto.getClassItem());
    }
    if (proto.hasMemberItem()) {
      return KeepMemberItemPattern.fromMemberProto(proto.getMemberItem(), getSymbol);
    }
    return defaultValue;
  }

  public ItemPattern.Builder buildItemProto() {
    ItemPattern.Builder builder = ItemPattern.newBuilder();
    match(
        clazz -> builder.setClassItem(clazz.buildClassProto()),
        member -> builder.setMemberItem(member.buildMemberProto()));
    return builder;
  }
}

