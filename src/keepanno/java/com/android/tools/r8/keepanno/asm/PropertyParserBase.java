// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

/** Special case of a property parser allowing only a single value callback. */
public abstract class PropertyParserBase<T, P, S> implements PropertyParser<T, P, S> {

  private String kind;
  private final Map<String, P> mapping = new HashMap<>();
  private String resultPropertyName = null;
  private T resultValue = null;

  boolean tryProperty(P property, String name, Object value, Consumer<T> setValue) {
    return false;
  }

  public boolean tryPropertyEnum(
      P property, String name, String descriptor, String value, Consumer<T> setValue) {
    return false;
  }

  AnnotationVisitor tryPropertyArray(P property, String name, Consumer<T> setValue) {
    return null;
  }

  AnnotationVisitor tryPropertyAnnotation(
      P property, String name, String descriptor, Consumer<T> setValue) {
    return null;
  }

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

  public T getValue() {
    assert (resultPropertyName != null) == (resultValue != null);
    return resultValue;
  }

  public T getValueOrDefault(T defaultValue) {
    assert (resultPropertyName != null) == (resultValue != null);
    return isDeclared() ? resultValue : defaultValue;
  }

  /** Helper for parsing directly. Returns non-null if the property-name triggered parsing. */
  public final T tryParse(String name, Object value) {
    boolean triggered = tryParse(name, value, unused -> {});
    assert triggered == (resultValue != null);
    return resultValue;
  }

  public String kind() {
    return kind != null ? kind : "";
  }

  public S setKind(String kind) {
    this.kind = kind;
    return self();
  }

  /** Add property parsing for the given property-name. */
  public S setProperty(P property, String name) {
    P old = mapping.put(name, property);
    if (old != null) {
      throw new IllegalArgumentException("Unexpected attempt to redefine property " + name);
    }
    return self();
  }

  @Override
  public final boolean tryParse(String name, Object value, Consumer<T> setValue) {
    P prop = mapping.get(name);
    if (prop != null) {
      return tryProperty(prop, name, value, wrap(name, setValue));
    }
    return false;
  }

  @Override
  public final boolean tryParseEnum(
      String name, String descriptor, String value, Consumer<T> setValue) {
    P prop = mapping.get(name);
    if (prop != null) {
      return tryPropertyEnum(prop, name, descriptor, value, wrap(name, setValue));
    }
    return false;
  }

  @Override
  public final AnnotationVisitor tryParseArray(String name, Consumer<T> setValue) {
    P prop = mapping.get(name);
    if (prop != null) {
      return tryPropertyArray(prop, name, wrap(name, setValue));
    }
    return null;
  }

  @Override
  public final AnnotationVisitor tryParseAnnotation(
      String name, String descriptor, Consumer<T> setValue) {
    P prop = mapping.get(name);
    if (prop != null) {
      return tryPropertyAnnotation(prop, name, descriptor, wrap(name, setValue));
    }
    return null;
  }
}
