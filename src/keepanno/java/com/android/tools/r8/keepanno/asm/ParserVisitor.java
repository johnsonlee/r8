// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.ParsingContext.AnnotationParsingContext;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.AnnotationVisitor;

/** Convert parser(s) into an annotation visitor. */
public class ParserVisitor extends AnnotationVisitorBase {

  private final List<PropertyParser<?, ?>> parsers;
  private final Runnable onVisitEnd;

  public ParserVisitor(
      AnnotationParsingContext parsingContext,
      String annotationDescriptor,
      List<PropertyParser<?, ?>> parsers,
      Runnable onVisitEnd) {
    super(parsingContext);
    this.parsers = parsers;
    this.onVisitEnd = onVisitEnd;
    assert annotationDescriptor.equals(parsingContext.getAnnotationDescriptor());
  }

  public ParserVisitor(
      AnnotationParsingContext parsingContext,
      String annotationDescriptor,
      PropertyParser<?, ?> declaration,
      Runnable onVisitEnd) {
    this(parsingContext, annotationDescriptor, Collections.singletonList(declaration), onVisitEnd);
  }

  private <T> void ignore(T unused) {}

  @Override
  public void visit(String name, Object value) {
    for (PropertyParser<?, ?> parser : parsers) {
      if (parser.tryParse(name, value, this::ignore)) {
        return;
      }
    }
    super.visit(name, value);
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    for (PropertyParser<?, ?> parser : parsers) {
      AnnotationVisitor visitor = parser.tryParseArray(name, this::ignore);
      if (visitor != null) {
        return visitor;
      }
    }
    return super.visitArray(name);
  }

  @Override
  public void visitEnum(String name, String descriptor, String value) {
    for (PropertyParser<?, ?> parser : parsers) {
      if (parser.tryParseEnum(name, descriptor, value, this::ignore)) {
        return;
      }
    }
    super.visitEnum(name, descriptor, value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String descriptor) {
    for (PropertyParser<?, ?> parser : parsers) {
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
