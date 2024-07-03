// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.Declaration;
import java.util.function.Consumer;
import java.util.function.Function;

/** Base class for the declarations represented in the keep annotations library. */
public abstract class KeepDeclaration {

  public abstract KeepEdgeMetaInfo getMetaInfo();

  public final <T> T apply(Function<KeepEdge, T> onEdge, Function<KeepCheck, T> onCheck) {
    if (isKeepEdge()) {
      return onEdge.apply(asKeepEdge());
    }
    return onCheck.apply(asKeepCheck());
  }

  public final void match(Consumer<KeepEdge> onEdge, Consumer<KeepCheck> onCheck) {
    apply(AstUtils.toVoidFunction(onEdge), AstUtils.toVoidFunction(onCheck));
  }

  public final boolean isKeepEdge() {
    return asKeepEdge() != null;
  }

  public KeepEdge asKeepEdge() {
    return null;
  }

  public final boolean isKeepCheck() {
    return asKeepCheck() != null;
  }

  public KeepCheck asKeepCheck() {
    return null;
  }

  @Override
  public final boolean equals(Object obj) {
    throw new RuntimeException();
  }

  @Override
  public final int hashCode() {
    throw new RuntimeException();
  }

  public final Declaration.Builder buildDeclarationProto() {
    Declaration.Builder builder = Declaration.newBuilder();
    return apply(
        edge -> builder.setEdge(edge.buildEdgeProto()),
        check -> builder.setCheck(check.buildCheckProto()));
  }

  public static KeepDeclaration fromProto(
      KeepSpecProtos.Declaration declaration, KeepSpecVersion version) {
    if (declaration.hasEdge()) {
      return KeepEdge.builder().applyProto(declaration.getEdge(), version).build();
    }
    if (declaration.hasCheck()) {
      return KeepCheck.builder().applyProto(declaration.getCheck(), version).build();
    }
    return null;
  }
}
