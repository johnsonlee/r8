// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassUnqualifiedNameParser.ClassUnqualifiedNameProperty;
import com.android.tools.r8.keepanno.asm.StringPatternParser.StringProperty;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public class ClassUnqualifiedNameParser
    extends PropertyParserBase<KeepUnqualfiedClassNamePattern, ClassUnqualifiedNameProperty> {

  private final StringPatternParser parser;

  public ClassUnqualifiedNameParser(ParsingContext parsingContext) {
    super(parsingContext);
    parser = new StringPatternParser(parsingContext);
  }

  public enum ClassUnqualifiedNameProperty {
    NAME,
    PATTERN
  }

  @Override
  public boolean tryProperty(
      ClassUnqualifiedNameProperty property,
      String name,
      Object value,
      Consumer<KeepUnqualfiedClassNamePattern> setValue) {
    switch (property) {
      case NAME:
        return parser.tryProperty(
            StringProperty.EXACT,
            name,
            value,
            stringPattern ->
                setValue.accept(KeepUnqualfiedClassNamePattern.fromStringPattern(stringPattern)));
      default:
        return false;
    }
  }

  @Override
  AnnotationVisitor tryPropertyAnnotation(
      ClassUnqualifiedNameProperty property,
      String name,
      String descriptor,
      Consumer<KeepUnqualfiedClassNamePattern> setValue) {
    switch (property) {
      case PATTERN:
        return parser.tryPropertyAnnotation(
            StringProperty.PATTERN,
            name,
            descriptor,
            stringPattern ->
                setValue.accept(KeepUnqualfiedClassNamePattern.fromStringPattern(stringPattern)));
      default:
        return super.tryPropertyAnnotation(property, name, descriptor, setValue);
    }
  }
}
