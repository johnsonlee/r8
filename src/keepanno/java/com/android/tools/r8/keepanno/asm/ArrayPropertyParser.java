// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.AnnotationVisitor;

public class ArrayPropertyParser<T, P> extends PropertyParserBase<List<T>, P> {

  private final Function<ParsingContext, PropertyParser<T, P>> elementParser;
  private List<T> values;

  public ArrayPropertyParser(
      ParsingContext parsingContext, Function<ParsingContext, PropertyParser<T, P>> elementParser) {
    super(parsingContext);
    this.elementParser = elementParser;
  }

  @Override
  AnnotationVisitor tryPropertyArray(P property, String name, Consumer<List<T>> setValue) {
    // The property name and type is forwarded to the element parser.
    values = new ArrayList<>();
    // The context is explicitly *not* extended with the property name here as it is forwarded.
    ParsingContext parsingContext = getParsingContext();
    return new AnnotationVisitorBase(parsingContext) {

      private PropertyParser<T, P> getParser() {
        PropertyParser<T, P> parser = elementParser.apply(parsingContext);
        getMapping().forEach(parser::setProperty);
        return parser;
      }

      @Override
      public void visitEnd() {
        setValue.accept(values);
      }

      @Override
      public void visit(String unusedName, Object value) {
        if (!getParser().tryParse(name, value, values::add)) {
          super.visit(name, value);
        }
      }

      @Override
      public AnnotationVisitor visitAnnotation(String unusedName, String descriptor) {
        AnnotationVisitor visitor = getParser().tryParseAnnotation(name, descriptor, values::add);
        if (visitor != null) {
          return visitor;
        }
        return super.visitAnnotation(name, descriptor);
      }

      @Override
      public void visitEnum(String unusedName, String descriptor, String value) {
        if (!getParser().tryParseEnum(name, descriptor, value, values::add)) {
          super.visitEnum(name, descriptor, value);
        }
      }

      @Override
      public AnnotationVisitor visitArray(String unusedName) {
        AnnotationVisitor visitor = getParser().tryParseArray(name, values::add);
        if (visitor != null) {
          return visitor;
        }
        return super.visitArray(name);
      }
    };
  }
}
