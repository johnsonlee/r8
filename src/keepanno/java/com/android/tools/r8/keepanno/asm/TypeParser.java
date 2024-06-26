// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassNameParser.ClassNameProperty;
import com.android.tools.r8.keepanno.asm.InstanceOfParser.InstanceOfProperties;
import com.android.tools.r8.keepanno.asm.TypeParser.TypeProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.TypePattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.AnnotationParsingContext;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

public class TypeParser extends PropertyParserBase<KeepTypePattern, TypeProperty> {

  public TypeParser(ParsingContext parsingContext) {
    super(parsingContext);
  }

  public enum TypeProperty {
    TYPE_PATTERN,
    TYPE_NAME,
    TYPE_CONSTANT,
    CLASS_NAME_PATTERN,
    INSTANCE_OF_PATTERN
  }

  @Override
  public boolean tryProperty(
      TypeProperty property, String name, Object value, Consumer<KeepTypePattern> setValue) {
    switch (property) {
      case TYPE_NAME:
        setValue.accept(
            KeepEdgeReaderUtils.typePatternFromString(
                (String) value, getParsingContext().property(name)));
        return true;
      case TYPE_CONSTANT:
        setValue.accept(KeepTypePattern.fromDescriptor(((Type) value).getDescriptor()));
        return true;
      default:
        return false;
    }
  }

  @Override
  public AnnotationVisitor tryPropertyAnnotation(
      TypeProperty property, String name, String descriptor, Consumer<KeepTypePattern> setValue) {
    switch (property) {
      case TYPE_PATTERN:
        {
          AnnotationParsingContext context =
              getParsingContext().property(name).annotation(descriptor);
          TypeParser typeParser = new TypeParser(context);
          typeParser.setProperty(TypePattern.name, TypeProperty.TYPE_NAME);
          typeParser.setProperty(TypePattern.constant, TypeProperty.TYPE_CONSTANT);
          typeParser.setProperty(TypePattern.classNamePattern, TypeProperty.CLASS_NAME_PATTERN);
          typeParser.setProperty(TypePattern.instanceOfPattern, TypeProperty.INSTANCE_OF_PATTERN);
          return new ParserVisitor(
              context,
              typeParser,
              () -> setValue.accept(typeParser.getValueOrDefault(KeepTypePattern.any())));
        }
      case CLASS_NAME_PATTERN:
        {
          ClassNameParser parser = new ClassNameParser(getParsingContext());
          return parser.tryPropertyAnnotation(
              ClassNameProperty.PATTERN,
              name,
              descriptor,
              value -> setValue.accept(KeepTypePattern.fromClass(value)));
        }
      case INSTANCE_OF_PATTERN:
        {
          InstanceOfParser parser = new InstanceOfParser(getParsingContext());
          return parser.tryPropertyAnnotation(
              InstanceOfProperties.PATTERN,
              name,
              descriptor,
              value -> setValue.accept(KeepTypePattern.fromInstanceOf(value)));
        }
      default:
        return null;
    }
  }
}
