// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepBindingReference;
import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalize a keep edge with respect to its bindings. This will systematically introduce a binding
 * for each item in the edge. It will also introduce a class binding for the holder of any member
 * item. By introducing a binding for each item the binding can be used as item identity.
 */
public class KeepEdgeNormalizer {

  private static final String syntheticBindingPrefix = "SyntheticBinding";

  public static KeepEdge normalize(KeepEdge edge) {
    // Check that all referenced bindings are defined.
    KeepEdgeNormalizer normalizer = new KeepEdgeNormalizer(edge);
    return normalizer.run();
  }

  private final KeepEdge edge;

  private final Map<KeepBindingSymbol, KeepItemPattern> normalizedUserBindings = new HashMap<>();
  private final KeepBindings.Builder bindingsBuilder = KeepBindings.builder();
  private final KeepPreconditions.Builder preconditionsBuilder = KeepPreconditions.builder();
  private final KeepConsequences.Builder consequencesBuilder = KeepConsequences.builder();

  private KeepEdgeNormalizer(KeepEdge edge) {
    this.edge = edge;
  }

  private KeepEdge run() {
    edge.getBindings()
        .forEach(
            (name, pattern) -> {
              KeepItemPattern normalizedItem = normalizeItemPattern(pattern);
              bindingsBuilder.addBinding(name, normalizedItem);
              normalizedUserBindings.put(name, normalizedItem);
            });
    // TODO(b/248408342): Normalize the preconditions by identifying vacuously true conditions.
    edge.getPreconditions()
        .forEach(
            condition ->
                preconditionsBuilder.addCondition(
                    KeepCondition.builder()
                        .setItemReference(normalizeItemReference(condition.getItem()))
                        .build()));
    edge.getConsequences()
        .forEachTarget(
            target -> {
              consequencesBuilder.addTarget(
                  KeepTarget.builder()
                      .setConstraints(target.getConstraints())
                      .setItemReference(normalizeItemReference(target.getItem()))
                      .build());
            });
    return KeepEdge.builder()
        .setMetaInfo(edge.getMetaInfo())
        .setBindings(bindingsBuilder.build())
        .setPreconditions(preconditionsBuilder.build())
        .setConsequences(consequencesBuilder.build())
        .build();
  }

  private KeepBindingReference normalizeItemReference(KeepBindingReference bindingReference) {
    if (bindingReference.isClassType()) {
      // A class-type reference is allowed to reference a member-typed binding.
      // In this case, the normalized reference is to the class of the member.
      KeepItemPattern boundItemPattern = normalizedUserBindings.get(bindingReference.getName());
      if (boundItemPattern.isMemberItemPattern()) {
        return boundItemPattern.asMemberItemPattern().getClassReference();
      }
    }
    return bindingReference;
  }

  private KeepItemPattern normalizeItemPattern(KeepItemPattern pattern) {
    if (pattern.isClassItemPattern()) {
      // If the pattern is just a class pattern it is in normal form.
      return pattern;
    }
    return normalizeMemberItemPattern(pattern.asMemberItemPattern());
  }

  private KeepMemberItemPattern normalizeMemberItemPattern(
      KeepMemberItemPattern memberItemPattern) {
    return memberItemPattern;
  }
}
