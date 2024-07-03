// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.AnnotatedByPattern;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.TypeDesc;
import java.util.function.Consumer;

public final class KeepSpecUtils {

  private KeepSpecUtils() {}

  public static TypeDesc desc(String descriptor) {
    return TypeDesc.newBuilder().setDesc(descriptor).build();
  }

  // Helper to resolve binding reference strings into symbols when parsing protos.
  public static class BindingResolver {

    // Builder is retained to resolve and does not escape this resolver.
    private final KeepBindings.Builder builder;

    // Bindings is the "build once" structure of the actual bindings.
    private final KeepBindings bindings;

    public BindingResolver(KeepBindings.Builder builder) {
      this.builder = builder;
      this.bindings = builder.build();
    }

    public KeepBindings getBindings() {
      return bindings;
    }

    public KeepBindingReference mapReference(KeepSpecProtos.BindingReference reference) {
      if (reference == null || reference.getName().isEmpty()) {
        throw new KeepEdgeException("Invalid binding reference");
      }
      return builder.getBindingReferenceForUserBinding(reference.getName());
    }
  }

  // Helpers to read/write the annotated-by pattern which does not have its own AST node.
  public static void buildAnnotatedByProto(
      OptionalPattern<KeepQualifiedClassNamePattern> pattern,
      Consumer<AnnotatedByPattern.Builder> callback) {
    // If the annotated-by pattern is absent then no restrictions are present, and we don't set it.
    if (pattern.isPresent()) {
      callback.accept(AnnotatedByPattern.newBuilder().setName(pattern.get().buildProto()));
    }
  }

  public static OptionalPattern<KeepQualifiedClassNamePattern> annotatedByFromProto(
      AnnotatedByPattern proto) {
    if (!proto.hasName()) {
      // No name implies any annotation (in contrast to no restrictions).
      return OptionalPattern.of(KeepQualifiedClassNamePattern.any());
    }
    return OptionalPattern.of(KeepQualifiedClassNamePattern.fromProto(proto.getName()));
  }
}
