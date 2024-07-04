// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassNameParser.ClassNameProperty;
import com.android.tools.r8.keepanno.asm.ClassUnqualifiedNameParser.ClassUnqualifiedNameProperty;
import com.android.tools.r8.keepanno.asm.PackageNameParser.PackageNameProperty;
import com.android.tools.r8.keepanno.asm.TypeParser.TypeProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.ClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepClassPattern;
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
                type ->
                    setValue.accept(typeToClassNameType(type, getParsingContext().property(name))));
      case CONSTANT:
        return new TypeParser(getParsingContext())
            .tryProperty(
                TypeProperty.TYPE_CONSTANT,
                name,
                value,
                type ->
                    setValue.accept(typeToClassNameType(type, getParsingContext().property(name))));
      default:
        return false;
    }
  }

  KeepQualifiedClassNamePattern typeToClassNameType(
      KeepTypePattern typePattern, PropertyParsingContext parsingContext) {
    return typePattern.apply(
        KeepQualifiedClassNamePattern::any,
        primitiveTypePattern -> {
          throw parsingContext.error("Invalid use of primitive type where class type was expected");
        },
        arrayTypePattern -> {
          throw parsingContext.error("Invalid use of array type where class type was expected");
        },
        KeepClassPattern::getClassNamePattern);
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
          ClassNameParser fullNameParser = new ClassNameParser(parsingContext);
          PackageNameParser packageParser = new PackageNameParser(parsingContext);
          ClassUnqualifiedNameParser unqualifiedNameParser =
              new ClassUnqualifiedNameParser(parsingContext);
          fullNameParser.setProperty(ClassNamePattern.name, ClassNameProperty.NAME);
          fullNameParser.setProperty(ClassNamePattern.constant, ClassNameProperty.CONSTANT);
          packageParser.setProperty(ClassNamePattern.packageName, PackageNameProperty.NAME);
          unqualifiedNameParser.setProperty(
              ClassNamePattern.unqualifiedName, ClassUnqualifiedNameProperty.NAME);
          unqualifiedNameParser.setProperty(
              ClassNamePattern.unqualifiedNamePattern, ClassUnqualifiedNameProperty.PATTERN);

          return new ParserVisitor(
              parsingContext,
              ImmutableList.of(fullNameParser, packageParser, unqualifiedNameParser),
              () -> {
                if (fullNameParser.isDeclared()) {
                  if (unqualifiedNameParser.isDeclared() || packageParser.isDeclared()) {
                    throw parsingContext.error(
                        "Cannot specify both the full class name and its "
                            + (unqualifiedNameParser.isDeclared()
                                ? "unqualified name"
                                : "package"));
                  }
                  setValue.accept(fullNameParser.getValue());
                  return;
                }
                setValue.accept(
                    KeepQualifiedClassNamePattern.builder()
                        .setPackagePattern(
                            packageParser.getValueOrDefault(KeepPackagePattern.any()))
                        .setNamePattern(
                            unqualifiedNameParser.getValueOrDefault(
                                KeepUnqualfiedClassNamePattern.any()))
                        .build());
              });
        }
      default:
        return null;
    }
  }
}
