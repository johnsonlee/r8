// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import java.util.function.Consumer;
import java.util.function.Function;
import org.objectweb.asm.AnnotationVisitor;

/**
 * Abstract base parser to help converting a parser of one type to another via delegation.
 *
 * @param <T1> Type of input parser.
 * @param <T2> Type of output parser (e.g., the parser subclassing this base).
 * @param <P> Properties (same for both parsers).
 */
public abstract class ConvertingPropertyParser<T1, T2, P> implements PropertyParser<T2, P> {

  private final PropertyParser<T1, P> parser;
  private final Function<T1, T2> converter;

  public ConvertingPropertyParser(PropertyParser<T1, P> parser, Function<T1, T2> converter) {
    this.parser = parser;
    this.converter = converter;
  }

  private Consumer<T1> wrap(Consumer<T2> fn) {
    return v1 -> fn.accept(converter.apply(v1));
  }

  @Override
  public void setProperty(String name, P property) {
    parser.setProperty(name, property);
  }

  @Override
  public boolean isDeclared() {
    return parser.isDeclared();
  }

  @Override
  public T2 getValue() {
    return converter.apply(parser.getValue());
  }

  @Override
  public T2 tryParse(String name, Object value) {
    T1 t1 = parser.tryParse(name, value);
    return t1 == null ? null : converter.apply(t1);
  }

  @Override
  public boolean tryParseEnum(String name, String descriptor, String value, Consumer<T2> setValue) {
    return parser.tryParseEnum(name, descriptor, value, wrap(setValue));
  }

  @Override
  public AnnotationVisitor tryParseArray(String name, Consumer<T2> setValue) {
    return parser.tryParseArray(name, wrap(setValue));
  }

  @Override
  public AnnotationVisitor tryParseAnnotation(
      String name, String descriptor, Consumer<T2> setValue) {
    return parser.tryParseAnnotation(name, descriptor, wrap(setValue));
  }
}
