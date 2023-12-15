// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

public abstract class PropertyParser<T, P, S> {

  private String kind;
  private final Map<String, P> mapping = new HashMap<>();

  abstract S self();

  abstract boolean tryProperty(P property, String name, Object value, Consumer<T> setValue);

  abstract AnnotationVisitor tryPropertyArray(P property, String name, Consumer<T> setValue);

  abstract AnnotationVisitor tryPropertyAnnotation(
      P property, String name, String descriptor, Consumer<T> setValue);

  String kind() {
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

  /** Parse a property. Returns true if the property-name triggered parsing. */
  public final boolean tryParse(String name, Object value, Consumer<T> setValue) {
    P prop = mapping.get(name);
    if (prop != null) {
      return tryProperty(prop, name, value, setValue);
    }
    return false;
  }

  /** Parse a property. Returns non-null if the property-name triggered parsing. */
  public final AnnotationVisitor tryParseArray(String name, Consumer<T> setValue) {
    P prop = mapping.get(name);
    if (prop != null) {
      return tryPropertyArray(prop, name, setValue);
    }
    return null;
  }

  /** Parse a property. Returns non-null if the property-name triggered parsing. */
  public final AnnotationVisitor tryParseAnnotation(
      String name, String descriptor, Consumer<T> setValue) {
    P prop = mapping.get(name);
    if (prop != null) {
      return tryPropertyAnnotation(prop, name, descriptor, setValue);
    }
    return null;
  }
}
