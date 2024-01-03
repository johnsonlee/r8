// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassNameParser.ClassNameProperty;
import com.android.tools.r8.keepanno.asm.ClassSimpleNameParser.ClassSimpleNameProperty;
import com.android.tools.r8.keepanno.asm.PackageNameParser.PackageNameProperty;
import com.android.tools.r8.keepanno.asm.TypeParser.TypeProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.ClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.AnnotationParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.PropertyParsingContext;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public class ClassNameParser
    extends PropertyParserBase<KeepQualifiedClassNamePattern, ClassNameProperty> {

  public ClassNameParser(ParsingContext parsingContext) {
    super(parsingContext);
  }

  public enum ClassNameProperty {
    PATTERN,
    NAME,
    CONSTANT,
  }

  @Override
  boolean tryProperty(
      ClassNameProperty property,
      String name,
      Object value,
      Consumer<KeepQualifiedClassNamePattern> setValue) {
    switch (property) {
      case NAME:
        return new TypeParser(getParsingContext())
            .tryProperty(
                TypeProperty.TYPE_NAME,
                name,
                value,
                type -> setValue.accept(typeToClassType(type, getParsingContext().property(name))));
      case CONSTANT:
        return new TypeParser(getParsingContext())
            .tryProperty(
                TypeProperty.TYPE_CONSTANT,
                name,
                value,
                type -> setValue.accept(typeToClassType(type, getParsingContext().property(name))));
      default:
        return false;
    }
  }

  KeepQualifiedClassNamePattern typeToClassType(
      KeepTypePattern typePattern, PropertyParsingContext parsingContext) {
    return typePattern.match(
        KeepQualifiedClassNamePattern::any,
        primitiveTypePattern -> {
          throw parsingContext.error("Invalid use of primitive type where class type was expected");
        },
        arrayTypePattern -> {
          throw parsingContext.error("Invalid use of array type where class type was expected");
        },
        classNamePattern -> classNamePattern);
  }

  @Override
  AnnotationVisitor tryPropertyAnnotation(
      ClassNameProperty property,
      String name,
      String descriptor,
      Consumer<KeepQualifiedClassNamePattern> setValue) {
    switch (property) {
      case PATTERN:
        {
          AnnotationParsingContext parsingContext =
              getParsingContext().property(name).annotation(descriptor);
          PackageNameParser packageParser = new PackageNameParser(parsingContext);
          ClassSimpleNameParser nameParser = new ClassSimpleNameParser(parsingContext);
          packageParser.setProperty(ClassNamePattern.packageName, PackageNameProperty.NAME);
          nameParser.setProperty(ClassNamePattern.simpleName, ClassSimpleNameProperty.NAME);
          return new ParserVisitor(
              parsingContext,
              descriptor,
              ImmutableList.of(packageParser, nameParser),
              () ->
                  setValue.accept(
                      KeepQualifiedClassNamePattern.builder()
                          .setPackagePattern(
                              packageParser.getValueOrDefault(KeepPackagePattern.any()))
                          .setNamePattern(
                              nameParser.getValueOrDefault(KeepUnqualfiedClassNamePattern.any()))
                          .build()));
        }
      default:
        return null;
    }
  }
}
