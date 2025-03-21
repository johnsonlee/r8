// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.ClassNameParser.ClassNameProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.AnnotationPattern;
import com.android.tools.r8.keepanno.ast.KeepAnnotationPattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.AnnotationParsingContext;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.function.Consumer;
import kotlin.annotation.AnnotationRetention;
import org.objectweb.asm.AnnotationVisitor;

public class AnnotationPatternParser
    extends PropertyParserBase<KeepAnnotationPattern, AnnotationPatternParser.AnnotationProperty> {

  public enum AnnotationProperty {
    PATTERN
  }

  public AnnotationPatternParser(ParsingContext parsingContext) {
    super(parsingContext);
  }

  @Override
  AnnotationVisitor tryPropertyAnnotation(
      AnnotationProperty property,
      String name,
      String descriptor,
      Consumer<KeepAnnotationPattern> setValue) {
    switch (property) {
      case PATTERN:
        AnnotationParsingContext parsingContext =
            getParsingContext().property(name).annotation(descriptor);
        AnnotationDeclarationParser parser = new AnnotationDeclarationParser(parsingContext);
        return new ParserVisitor(
            parsingContext,
            parser,
            () ->
                setValue.accept(
                    parser.isDeclared()
                        ? parser.getValue()
                        : KeepAnnotationPattern.anyWithRuntimeRetention()));
      default:
        return super.tryPropertyAnnotation(property, name, descriptor, setValue);
    }
  }

  private enum RetentionProperty {
    RETENTION
  }

  private static class RetentionParser
      extends PropertyParserBase<RetentionPolicy, RetentionProperty> {

    // Standard Java RetentionPolicy.
    private static final String RETENTION_POLICY_DESC = "Ljava/lang/annotation/RetentionPolicy;";
    // Kotlin version of Java RetentionPolicy.
    private static final String ANNOTATION_RETENTION_DESC =
        "Lkotlin/annotation/AnnotationRetention;";

    public RetentionParser(ParsingContext parsingContext) {
      super(parsingContext);
    }

    @Override
    public boolean tryPropertyEnum(
        RetentionProperty property,
        String name,
        String descriptor,
        String value,
        Consumer<RetentionPolicy> setValue) {
      assert property == RetentionProperty.RETENTION;
      if (RETENTION_POLICY_DESC.equals(descriptor)) {
        setValue.accept(RetentionPolicy.valueOf(value));
        return true;
      }
      if (ANNOTATION_RETENTION_DESC.equals(descriptor)) {
        AnnotationRetention annotationRetention = AnnotationRetention.valueOf(value);
        switch (annotationRetention) {
          case BINARY:
            setValue.accept(RetentionPolicy.CLASS);
            return true;
          case RUNTIME:
            setValue.accept(RetentionPolicy.RUNTIME);
            return true;
        }
      }
      return super.tryPropertyEnum(property, name, descriptor, value, setValue);
    }
  }

  private static class AnnotationDeclarationParser
      extends DeclarationParser<KeepAnnotationPattern> {

    private final ClassNameParser nameParser;
    private final ArrayPropertyParser<RetentionPolicy, RetentionProperty> retentionParser;
    private final List<Parser<?>> parsers;

    public AnnotationDeclarationParser(ParsingContext parsingContext) {
      nameParser = new ClassNameParser(parsingContext);
      nameParser.setProperty(AnnotationPattern.name, ClassNameProperty.NAME);
      nameParser.setProperty(AnnotationPattern.constant, ClassNameProperty.CONSTANT);
      nameParser.setProperty(AnnotationPattern.namePattern, ClassNameProperty.PATTERN);
      retentionParser = new ArrayPropertyParser<>(parsingContext, RetentionParser::new);
      retentionParser.setProperty(AnnotationPattern.retention, RetentionProperty.RETENTION);
      retentionParser.setValueCheck(
          (value, propertyContext) -> {
            if (value.isEmpty()) {
              throw propertyContext.error("Expected non-empty array of retention policies");
            }
          });
      parsers = ImmutableList.of(nameParser, retentionParser);
    }

    @Override
    List<Parser<?>> parsers() {
      return parsers;
    }

    public KeepAnnotationPattern getValue() {
      if (isDefault()) {
        return null;
      }
      KeepAnnotationPattern.Builder builder = KeepAnnotationPattern.builder();
      if (retentionParser.isDeclared()) {
        List<RetentionPolicy> policies = retentionParser.getValue();
        policies.forEach(builder::addRetentionPolicy);
      } else {
        builder.addRetentionPolicy(RetentionPolicy.RUNTIME);
      }
      return builder
          .setNamePattern(nameParser.getValueOrDefault(KeepQualifiedClassNamePattern.any()))
          .build();
    }
  }
}
