// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.StringPatternParser.StringProperty;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.StringPattern;
import com.android.tools.r8.keepanno.ast.KeepStringPattern;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import com.android.tools.r8.keepanno.ast.ParsingContext.AnnotationParsingContext;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public class StringPatternParser extends PropertyParserBase<KeepStringPattern, StringProperty> {

  public StringPatternParser(ParsingContext parsingContext) {
    super(parsingContext);
  }

  public enum StringProperty {
    EXACT,
    PATTERN
  }

  @Override
  boolean tryProperty(
      StringProperty property, String name, Object value, Consumer<KeepStringPattern> setValue) {
    switch (property) {
      case EXACT:
        {
          setValue.accept(KeepStringPattern.exact((String) value));
          return true;
        }
      default:
        return false;
    }
  }

  @Override
  AnnotationVisitor tryPropertyAnnotation(
      StringProperty property,
      String name,
      String descriptor,
      Consumer<KeepStringPattern> setValue) {
    switch (property) {
      case PATTERN:
        {
          AnnotationParsingContext parsingContext =
              getParsingContext().property(name).annotation(descriptor);
          StringPatternParser exactParser = new StringPatternParser(parsingContext);
          StringParser prefixParser = new StringParser(parsingContext);
          StringParser suffixParser = new StringParser(parsingContext);
          exactParser.setProperty(StringPattern.exact, StringProperty.EXACT);
          prefixParser.setProperty(StringPattern.startsWith, StringParser.Property.STRING);
          suffixParser.setProperty(StringPattern.endsWith, StringParser.Property.STRING);
          return new ParserVisitor(
              parsingContext,
              descriptor,
              ImmutableList.of(exactParser, prefixParser, suffixParser),
              () -> {
                if (exactParser.isDeclared()) {
                  if (prefixParser.isDeclared()) {
                    throw parsingContext.error("Cannot specify both the exact string and a prefix");
                  }
                  if (suffixParser.isDeclared()) {
                    throw parsingContext.error("Cannot specify both the exact string and a suffix");
                  }
                  setValue.accept(exactParser.getValue());
                } else {
                  setValue.accept(
                      KeepStringPattern.builder()
                          .setPrefix(prefixParser.getValueOrDefault(null))
                          .setSuffix(suffixParser.getValueOrDefault(null))
                          .build());
                }
              });
        }
      default:
        return null;
    }
  }

  private static class StringParser extends PropertyParserBase<String, StringParser.Property> {

    enum Property {
      STRING
    }

    protected StringParser(ParsingContext parsingContext) {
      super(parsingContext);
    }

    @Override
    boolean tryProperty(
        StringParser.Property property, String name, Object value, Consumer<String> setValue) {
      assert Property.STRING.equals(property);
      if (value instanceof String) {
        setValue.accept((String) value);
      }
      return false;
    }
  }
}
