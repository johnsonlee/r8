// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.TypeParser.TypeProperty;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

/**
 * Parser for parsing method return types.
 *
 * <p>This parser wraps a type parser and adds support for parsing the string name {@code "void"} or
 * the class constant {@code void.class} as the void method return type.
 */
public class MethodReturnTypeParser
    extends PropertyParserBase<KeepMethodReturnTypePattern, TypeProperty> {

  private final TypeParser typeParser;

  public MethodReturnTypeParser(ParsingContext parsingContext) {
    super(parsingContext);
    typeParser = new TypeParser(parsingContext);
  }

  static Consumer<KeepTypePattern> wrap(Consumer<KeepMethodReturnTypePattern> fn) {
    return t -> fn.accept(KeepMethodReturnTypePattern.fromType(t));
  }

  @Override
  public KeepMethodReturnTypePattern getValue() {
    return super.getValue();
  }

  @Override
  boolean tryProperty(
      TypeProperty property,
      String name,
      Object value,
      Consumer<KeepMethodReturnTypePattern> setValue) {
    if (property == TypeProperty.TYPE_NAME && "void".equals(value)) {
      setValue.accept(KeepMethodReturnTypePattern.voidType());
      return true;
    }
    if (property == TypeProperty.TYPE_CONSTANT && Type.getType("V").equals(value)) {
      setValue.accept(KeepMethodReturnTypePattern.voidType());
      return true;
    }
    return typeParser.tryProperty(property, name, value, wrap(setValue));
  }

  @Override
  public boolean tryPropertyEnum(
      TypeProperty property,
      String name,
      String descriptor,
      String value,
      Consumer<KeepMethodReturnTypePattern> setValue) {
    return typeParser.tryPropertyEnum(property, name, descriptor, value, wrap(setValue));
  }

  @Override
  AnnotationVisitor tryPropertyArray(
      TypeProperty property, String name, Consumer<KeepMethodReturnTypePattern> setValue) {
    return typeParser.tryPropertyArray(property, name, wrap(setValue));
  }

  @Override
  AnnotationVisitor tryPropertyAnnotation(
      TypeProperty property,
      String name,
      String descriptor,
      Consumer<KeepMethodReturnTypePattern> setValue) {
    return typeParser.tryPropertyAnnotation(property, name, descriptor, wrap(setValue));
  }
}
