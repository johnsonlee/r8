// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

/** Convert a parser into an annotation visitor. */
public class ParserVisitor<T, P, S> extends AnnotationVisitorBase {

  private final String annotationDescriptor;
  private final SingleValuePropertyParser<T, P, S> declaration;
  private final Consumer<T> callback;

  public ParserVisitor(
      String annotationDescriptor,
      SingleValuePropertyParser<T, P, S> declaration,
      Consumer<T> callback) {
    this.annotationDescriptor = annotationDescriptor;
    this.declaration = declaration;
    this.callback = callback;
  }

  @Override
  public String getAnnotationName() {
    int start = annotationDescriptor.lastIndexOf('/') + 1;
    int end = annotationDescriptor.length() - 1;
    return annotationDescriptor.substring(start, end);
  }

  @Override
  public void visit(String name, Object value) {
    if (declaration.tryParse(name, value, unused -> {})) {
      return;
    }
    super.visit(name, value);
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    AnnotationVisitor visitor = declaration.tryParseArray(name, unused -> {});
    if (visitor != null) {
      return visitor;
    }
    return super.visitArray(name);
  }

  @Override
  public void visitEnum(String name, String descriptor, String value) {
    super.visitEnum(name, descriptor, value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String descriptor) {
    AnnotationVisitor visitor = declaration.tryParseAnnotation(name, descriptor, unused -> {});
    if (visitor != null) {
      return visitor;
    }
    return super.visitAnnotation(name, descriptor);
  }

  @Override
  public void visitEnd() {
    if (declaration.isDeclared()) {
      callback.accept(declaration.getSingleValue());
    }
  }
}
