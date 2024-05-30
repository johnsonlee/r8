// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassSimpleNameParser.ClassSimpleNameProperty;
import com.android.tools.r8.keepanno.asm.StringPatternParser.StringProperty;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public class ClassSimpleNameParser
    extends PropertyParserBase<KeepUnqualfiedClassNamePattern, ClassSimpleNameProperty> {

  private final StringPatternParser parser;

  public ClassSimpleNameParser(ParsingContext parsingContext) {
    super(parsingContext);
    parser = new StringPatternParser(parsingContext);
  }

  public enum ClassSimpleNameProperty {
    NAME,
    PATTERN
  }

  @Override
  public boolean tryProperty(
      ClassSimpleNameProperty property,
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
      ClassSimpleNameProperty property,
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
