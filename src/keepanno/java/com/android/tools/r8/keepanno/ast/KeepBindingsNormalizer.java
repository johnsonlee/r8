// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.Binding;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Verify and normalize a set of bindings.
 *
 * <p>This does a few things as part of normalization:
 *
 * <ul>
 *   <li>It verifies that bindings are defined and type correct (no member-reference of a class
 *       binding).
 *   <li>It replaces class-references to members by a reference to the member's holder class.
 *   <li>It removes unused bindings.
 * </ul>
 */
public class KeepBindingsNormalizer {

  public static KeepBindingsNormalizer create(KeepBindings bindings) {
    return new KeepBindingsNormalizer(bindings);
  }

  private KeepBindings bindings;

  // Set of bindings that are used via 'register'. Building the final bindings will trim down
  // the actual bindings based on this set.
  private final Set<KeepBindingSymbol> used = new HashSet<>();

  // Bit to track if any references are actually updated.
  // If not, then rebuilding can be avoided.
  private boolean replacedReferences = false;

  private KeepBindingsNormalizer(KeepBindings bindings) {
    this.bindings = bindings;
  }

  /* True if any binding reference needed to be changed. */
  public boolean didReplaceReferences() {
    return replacedReferences;
  }

  public KeepBindings buildBindings() {
    // Fast path will typically use all bindings.
    if (bindings.size() == used.size()) {
      return bindings;
    }
    KeepBindings.Builder builder = KeepBindings.builder();
    for (KeepBindingSymbol symbol : used) {
      builder.addBinding(symbol, bindings.get(symbol).getItem());
    }
    // Guard against use after building.
    bindings = null;
    return builder.build();
  }

  public <T> T registerAndNormalizeReference(
      KeepBindingReference reference,
      T defaultValue,
      BiFunction<KeepBindingReference, T, T> newValueBuilder) {
    KeepBindingSymbol symbol = reference.getName();
    Binding binding = bindings.get(symbol);
    if (binding == null) {
      throw new KeepEdgeException("Unbound reference to '" + symbol + "'");
    }
    KeepItemPattern item = binding.getItem();
    KeepMemberItemPattern memberItem = item.asMemberItemPattern();
    // Mark the binding as used and if it is a member also mark its class as used.
    used.add(symbol);
    if (memberItem != null) {
      used.add(memberItem.getClassReference().getName());
    }
    T value = defaultValue;
    if (reference.isClassType() && memberItem != null) {
      // A class-type reference is allowed to reference a member-typed binding.
      // In this case, the normalized reference is to the class of the member.
      KeepClassBindingReference classReference = memberItem.getClassReference();
      value = newValueBuilder.apply(classReference, defaultValue);
      replacedReferences = true;
    } else if (reference.isMemberType() && item.isClassItemPattern()) {
      throw new KeepEdgeException(
          "Invalid member-reference the class-type binding '" + symbol + "'");
    }
    return value;
  }
}
