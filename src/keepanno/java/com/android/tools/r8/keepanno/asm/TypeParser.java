// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.TypeParser.Properties;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.TypePattern;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

public class TypeParser extends SingleValuePropertyParser<KeepTypePattern, Properties, TypeParser> {

  public enum Properties {
    SELF_PATTERN,
    TYPE_NAME,
    TYPE_CONSTANT,
    CLASS_NAME_PATTERN
  }

  public TypeParser enableTypePattern(String propertyName) {
    return setProperty(Properties.SELF_PATTERN, propertyName);
  }

  public TypeParser enableTypeName(String propertyName) {
    return setProperty(Properties.TYPE_NAME, propertyName);
  }

  public TypeParser enableTypeConstant(String propertyName) {
    return setProperty(Properties.TYPE_CONSTANT, propertyName);
  }

  public TypeParser enableTypeClassNamePattern(String propertyName) {
    return setProperty(Properties.CLASS_NAME_PATTERN, propertyName);
  }

  @Override
  TypeParser self() {
    return this;
  }

  @Override
  public boolean trySingleProperty(
      Properties property, String name, Object value, Consumer<KeepTypePattern> setValue) {
    switch (property) {
      case TYPE_NAME:
        setValue.accept(KeepEdgeReaderUtils.typePatternFromString((String) value));
        return true;
      case TYPE_CONSTANT:
        setValue.accept(KeepTypePattern.fromDescriptor(((Type) value).getDescriptor()));
        return true;
      default:
        return false;
    }
  }

  @Override
  public AnnotationVisitor trySinglePropertyArray(
      Properties property, String name, Consumer<KeepTypePattern> setValue) {
    return null;
  }

  @Override
  public AnnotationVisitor trySinglePropertyAnnotation(
      Properties property, String name, String descriptor, Consumer<KeepTypePattern> setValue) {
    switch (property) {
      case SELF_PATTERN:
        return new ParserVisitor<>(
            descriptor,
            new TypeParser()
                .setKind(kind())
                .enableTypeName(TypePattern.name)
                .enableTypeConstant(TypePattern.constant)
                .enableTypeClassNamePattern(TypePattern.classNamePattern),
            setValue);
      case CLASS_NAME_PATTERN:
        throw new Unimplemented("Non-exact class patterns are not unimplemented yet");
      default:
        return null;
    }
  }
}
