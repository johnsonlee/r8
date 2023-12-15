// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

/** Special case of a property parser allowing only a single value callback. */
public abstract class SingleValuePropertyParser<T, P, S> extends PropertyParser<T, P, S> {

  private String resultPropertyName = null;
  private T resultValue = null;

  abstract boolean trySingleProperty(P property, String name, Object value, Consumer<T> setValue);

  abstract AnnotationVisitor trySinglePropertyArray(P property, String name, Consumer<T> setValue);

  abstract AnnotationVisitor trySinglePropertyAnnotation(
      P property, String name, String descriptor, Consumer<T> setValue);

  private Consumer<T> wrap(String propertyName, Consumer<T> setValue) {
    return value -> {
      assert value != null;
      if (resultPropertyName != null) {
        assert resultValue != null;
        error(propertyName);
      } else {
        resultPropertyName = propertyName;
        resultValue = value;
        setValue.accept(value);
      }
    };
  }

  private void error(String name) {
    throw new KeepEdgeException(
        "Multiple properties defining "
            + kind()
            + ": '"
            + resultPropertyName
            + "' and '"
            + name
            + "'");
  }

  public final boolean isDeclared() {
    assert (resultPropertyName != null) == (resultValue != null);
    return resultPropertyName != null;
  }

  public T getSingleValue() {
    assert (resultPropertyName != null) == (resultValue != null);
    return resultValue;
  }

  /** Helper for parsing directly. Returns non-null if the property-name triggered parsing. */
  public final T tryParse(String name, Object value) {
    boolean triggered = tryParse(name, value, unused -> {});
    assert triggered == (resultValue != null);
    return resultValue;
  }

  @Override
  final boolean tryProperty(P property, String name, Object value, Consumer<T> setValue) {
    return trySingleProperty(property, name, value, wrap(name, setValue));
  }

  @Override
  final AnnotationVisitor tryPropertyArray(P property, String name, Consumer<T> setValue) {
    return trySinglePropertyArray(property, name, wrap(name, setValue));
  }

  @Override
  final AnnotationVisitor tryPropertyAnnotation(
      P property, String name, String descriptor, Consumer<T> setValue) {
    return trySinglePropertyAnnotation(property, name, descriptor, wrap(name, setValue));
  }
}
