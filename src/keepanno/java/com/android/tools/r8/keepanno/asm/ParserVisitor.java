// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import java.util.Collections;
import java.util.List;
import org.objectweb.asm.AnnotationVisitor;

/** Convert parser(s) into an annotation visitor. */
public class ParserVisitor extends AnnotationVisitorBase {

  private final String annotationDescriptor;
  private final List<PropertyParser<?, ?, ?>> parsers;
  private final Runnable onVisitEnd;

  public ParserVisitor(
      String annotationDescriptor, List<PropertyParser<?, ?, ?>> parsers, Runnable onVisitEnd) {
    this.annotationDescriptor = annotationDescriptor;
    this.parsers = parsers;
    this.onVisitEnd = onVisitEnd;
  }

  public ParserVisitor(
      String annotationDescriptor, PropertyParser<?, ?, ?> declaration, Runnable onVisitEnd) {
    this(annotationDescriptor, Collections.singletonList(declaration), onVisitEnd);
  }

  private <T> void ignore(T unused) {}

  @Override
  public String getAnnotationName() {
    int start = annotationDescriptor.lastIndexOf('/') + 1;
    int end = annotationDescriptor.length() - 1;
    return annotationDescriptor.substring(start, end);
  }

  @Override
  public void visit(String name, Object value) {
    for (PropertyParser<?, ?, ?> parser : parsers) {
      if (parser.tryParse(name, value, this::ignore)) {
        return;
      }
    }
    super.visit(name, value);
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    for (PropertyParser<?, ?, ?> parser : parsers) {
      AnnotationVisitor visitor = parser.tryParseArray(name, this::ignore);
      if (visitor != null) {
        return visitor;
      }
    }
    return super.visitArray(name);
  }

  @Override
  public void visitEnum(String name, String descriptor, String value) {
    for (PropertyParser<?, ?, ?> parser : parsers) {
      if (parser.tryParseEnum(name, descriptor, value, this::ignore)) {
        return;
      }
    }
    super.visitEnum(name, descriptor, value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String descriptor) {
    for (PropertyParser<?, ?, ?> parser : parsers) {
      AnnotationVisitor visitor = parser.tryParseAnnotation(name, descriptor, this::ignore);
      if (visitor != null) {
        return visitor;
      }
    }
    return super.visitAnnotation(name, descriptor);
  }

  @Override
  public void visitEnd() {
    onVisitEnd.run();
    super.visitEnd();
  }
}
