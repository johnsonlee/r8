// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlin.metadata.Attributes;
import kotlin.metadata.KmClass;
import kotlin.metadata.KmConstructor;
import kotlin.metadata.KmEffectExpression;
import kotlin.metadata.KmProperty;
import kotlin.metadata.KmPropertyAccessorAttributes;
import kotlin.metadata.jvm.JvmAttributes;

public class KotlinFlagUtils {

  static void copyAllFlags(KmProperty src, KmProperty dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setModality(dest, Attributes.getModality(src));
    Attributes.setKind(dest, Attributes.getKind(src));
    Attributes.setVar(dest, Attributes.isVar(src));
    Attributes.setConst(dest, Attributes.isConst(src));
    Attributes.setLateinit(dest, Attributes.isLateinit(src));
    Attributes.setHasConstant(dest, Attributes.getHasConstant(src));
    Attributes.setExternal(dest, Attributes.isExternal(src));
    Attributes.setDelegated(dest, Attributes.isDelegated(src));
    Attributes.setExpect(dest, Attributes.isExpect(src));

    JvmAttributes.setMovedFromInterfaceCompanion(
        dest, JvmAttributes.isMovedFromInterfaceCompanion(src));
  }

  static void copyAllFlags(KmPropertyAccessorAttributes src, KmPropertyAccessorAttributes dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setModality(dest, Attributes.getModality(src));
    Attributes.setNotDefault(dest, Attributes.isNotDefault(src));
    Attributes.setExternal(dest, Attributes.isExternal(src));
    Attributes.setInline(dest, Attributes.isInline(src));
  }

  static void copyAllFlags(KmClass src, KmClass dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setModality(dest, Attributes.getModality(src));
    Attributes.setKind(dest, Attributes.getKind(src));
    Attributes.setInner(dest, Attributes.isInner(src));
    Attributes.setData(dest, Attributes.isData(src));
    Attributes.setExternal(dest, Attributes.isExternal(src));
    Attributes.setExpect(dest, Attributes.isExpect(src));
    Attributes.setValue(dest, Attributes.isValue(src));
    Attributes.setFunInterface(dest, Attributes.isFunInterface(src));
    Attributes.setHasEnumEntries(dest, Attributes.getHasEnumEntries(src));

    JvmAttributes.setCompiledInCompatibilityMode(
        dest, JvmAttributes.isCompiledInCompatibilityMode(src));
    JvmAttributes.setHasMethodBodiesInInterface(
        dest, JvmAttributes.getHasMethodBodiesInInterface(src));
  }

  static void copyAllFlags(KmConstructor src, KmConstructor dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setSecondary(dest, Attributes.isSecondary(src));
    Attributes.setHasNonStableParameterNames(dest, Attributes.getHasNonStableParameterNames(src));
  }

  static void copyAllFlags(KmEffectExpression src, KmEffectExpression dest) {
    Attributes.setNegated(dest, Attributes.isNegated(src));
    Attributes.setNullCheckPredicate(dest, Attributes.isNullCheckPredicate(src));
  }
}
