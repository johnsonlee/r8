// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import java.util.HashMap;
import java.util.Map;
import kotlin.metadata.Attributes;
import kotlin.metadata.KmClass;
import kotlin.metadata.KmConstructor;
import kotlin.metadata.KmEffectExpression;
import kotlin.metadata.KmFunction;
import kotlin.metadata.KmProperty;
import kotlin.metadata.KmPropertyAccessorAttributes;
import kotlin.metadata.KmType;
import kotlin.metadata.KmTypeAlias;
import kotlin.metadata.KmTypeParameter;
import kotlin.metadata.KmValueParameter;
import kotlin.metadata.jvm.JvmAttributes;

public class KotlinFlagUtils {

  private static final String ANNOTATIONS_KEY = "hasAnnotations";
  private static final String VISIBILITY_KEY = "visibility";
  private static final String MODALITY_KEY = "modality";
  private static final String KIND_KEY = "kind";
  private static final String INNER_KEY = "inner";
  private static final String DATA_KEY = "data";
  private static final String VALUE_KEY = "value";
  private static final String FUN_INTERFACE_KEY = "funInterface";
  private static final String ENUM_ENTRIES_KEY = "enumEntries";
  private static final String VAR_KEY = "var";
  private static final String CONST_KEY = "const";
  private static final String LATE_INIT_KEY = "lateInit";
  private static final String CONSTANT_KEY = "hasConstant";
  private static final String EXTERNAL_KEY = "external";
  private static final String DELEGATED_KEY = "delegated";
  private static final String EXPECT_KEY = "expect";
  private static final String NOT_DEFAULT_KEY = "notDefault";
  private static final String INLINE_KEY = "inline";
  private static final String SECONDARY_KEY = "secondary";
  private static final String NON_STABLE_PARAMETER_NAMES_KEY = "nonStableParameterNames";
  private static final String NEGATED_KEY = "negated";
  private static final String NULL_CHECK_PREDICATE_KEY = "nullCheckPredicate";
  private static final String OPERATOR_KEY = "operator";
  private static final String INFIX_KEY = "infix";
  private static final String TAIL_REC_KEY = "tailRec";
  private static final String SUSPEND_KEY = "suspend";
  private static final String NULLABLE_KEY = "nullable";
  private static final String DEFINITELY_NON_NULL_KEY = "definitelyNonNull";
  private static final String DECLARES_DEFAULT_VALUE_KEY = "declaresDefaultValue";
  private static final String CROSS_INLINE_KEY = "crossInline";
  private static final String NO_INLINE_KEY = "noInline";
  private static final String REIFIED_KEY = "reified_key";

  public static Map<String, Object> extractFlags(KmProperty src) {
    Map<String, Object> map = new HashMap<>();
    map.put(ANNOTATIONS_KEY, Attributes.getHasAnnotations(src));
    map.put(VISIBILITY_KEY, Attributes.getVisibility(src));
    map.put(MODALITY_KEY, Attributes.getModality(src));
    map.put(KIND_KEY, Attributes.getKind(src));
    map.put(VAR_KEY, Attributes.isVar(src));
    map.put(CONST_KEY, Attributes.isConst(src));
    map.put(LATE_INIT_KEY, Attributes.isLateinit(src));
    map.put(CONSTANT_KEY, Attributes.getHasConstant(src));
    map.put(EXTERNAL_KEY, Attributes.isExternal(src));
    map.put(DELEGATED_KEY, Attributes.isDelegated(src));
    map.put(EXPECT_KEY, Attributes.isExpect(src));

    map.put("movedFromInterfaceCompanion", JvmAttributes.isMovedFromInterfaceCompanion(src));
    return map;
  }

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

  public static Map<String, Object> extractFlags(KmType src) {
    Map<String, Object> map = new HashMap<>();
    map.put(NULLABLE_KEY, Attributes.isNullable(src));
    map.put(SUSPEND_KEY, Attributes.isSuspend(src));
    map.put(DEFINITELY_NON_NULL_KEY, Attributes.isDefinitelyNonNull(src));
    return map;
  }

  static void copyAllFlags(KmType src, KmType dest) {
    Attributes.setNullable(dest, Attributes.isNullable(src));
    Attributes.setSuspend(dest, Attributes.isSuspend(src));
    Attributes.setDefinitelyNonNull(dest, Attributes.isDefinitelyNonNull(src));
  }

  public static Map<String, Object> extractFlags(KmPropertyAccessorAttributes src) {
    Map<String, Object> map = new HashMap<>();
    map.put(ANNOTATIONS_KEY, Attributes.getHasAnnotations(src));
    map.put(VISIBILITY_KEY, Attributes.getVisibility(src));
    map.put(MODALITY_KEY, Attributes.getModality(src));
    map.put(NOT_DEFAULT_KEY, Attributes.isNotDefault(src));
    map.put(EXTERNAL_KEY, Attributes.isExternal(src));
    map.put(INLINE_KEY, Attributes.isInline(src));
    return map;
  }

  static void copyAllFlags(KmPropertyAccessorAttributes src, KmPropertyAccessorAttributes dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setModality(dest, Attributes.getModality(src));
    Attributes.setNotDefault(dest, Attributes.isNotDefault(src));
    Attributes.setExternal(dest, Attributes.isExternal(src));
    Attributes.setInline(dest, Attributes.isInline(src));
  }

  public static Map<String, Object> extractFlags(KmClass src) {
    Map<String, Object> map = new HashMap<>();
    map.put(ANNOTATIONS_KEY, Attributes.getHasAnnotations(src));
    map.put(VISIBILITY_KEY, Attributes.getVisibility(src));
    map.put(MODALITY_KEY, Attributes.getModality(src));
    map.put(KIND_KEY, Attributes.getKind(src));
    map.put(INNER_KEY, Attributes.isInner(src));
    map.put(DATA_KEY, Attributes.isData(src));
    map.put(EXTERNAL_KEY, Attributes.isExternal(src));
    map.put(EXPECT_KEY, Attributes.isExpect(src));
    map.put(VALUE_KEY, Attributes.isValue(src));
    map.put(FUN_INTERFACE_KEY, Attributes.isFunInterface(src));
    map.put(ENUM_ENTRIES_KEY, Attributes.getHasEnumEntries(src));

    map.put("compiledInCompatibilityMode", JvmAttributes.isCompiledInCompatibilityMode(src));
    map.put("hasMethodBodiesInInterface", JvmAttributes.getHasMethodBodiesInInterface(src));
    return map;
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

  public static Map<String, Object> extractFlags(KmConstructor src) {
    Map<String, Object> map = new HashMap<>();
    map.put(ANNOTATIONS_KEY, Attributes.getHasAnnotations(src));
    map.put(VISIBILITY_KEY, Attributes.getVisibility(src));
    map.put(SECONDARY_KEY, Attributes.isSecondary(src));
    map.put(NON_STABLE_PARAMETER_NAMES_KEY, Attributes.getHasNonStableParameterNames(src));
    return map;
  }

  static void copyAllFlags(KmConstructor src, KmConstructor dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setSecondary(dest, Attributes.isSecondary(src));
    Attributes.setHasNonStableParameterNames(dest, Attributes.getHasNonStableParameterNames(src));
  }

  public static Map<String, Object> extractFlags(KmFunction src) {
    Map<String, Object> map = new HashMap<>();
    map.put(ANNOTATIONS_KEY, Attributes.getHasAnnotations(src));
    map.put(KIND_KEY, Attributes.getKind(src));
    map.put(MODALITY_KEY, Attributes.getModality(src));
    map.put(OPERATOR_KEY, Attributes.isOperator(src));
    map.put(INFIX_KEY, Attributes.isInfix(src));
    map.put(INLINE_KEY, Attributes.isInline(src));
    map.put(TAIL_REC_KEY, Attributes.isTailrec(src));
    map.put(EXTERNAL_KEY, Attributes.isExternal(src));
    map.put(SUSPEND_KEY, Attributes.isSuspend(src));
    map.put(EXPECT_KEY, Attributes.isExpect(src));
    map.put(VISIBILITY_KEY, Attributes.getVisibility(src));
    map.put(NON_STABLE_PARAMETER_NAMES_KEY, Attributes.getHasNonStableParameterNames(src));
    return map;
  }

  static void copyAllFlags(KmFunction src, KmFunction dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setKind(dest, Attributes.getKind(src));
    Attributes.setModality(dest, Attributes.getModality(src));
    Attributes.setOperator(dest, Attributes.isOperator(src));
    Attributes.setInfix(dest, Attributes.isInfix(src));
    Attributes.setInline(dest, Attributes.isInline(src));
    Attributes.setTailrec(dest, Attributes.isTailrec(src));
    Attributes.setExternal(dest, Attributes.isExternal(src));
    Attributes.setSuspend(dest, Attributes.isSuspend(src));
    Attributes.setExpect(dest, Attributes.isExpect(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
    Attributes.setHasNonStableParameterNames(dest, Attributes.getHasNonStableParameterNames(src));
  }

  public static Map<String, Object> extractFlags(KmEffectExpression src) {
    Map<String, Object> map = new HashMap<>();
    map.put(NEGATED_KEY, Attributes.isNegated(src));
    map.put(NULL_CHECK_PREDICATE_KEY, Attributes.isNullCheckPredicate(src));
    return map;
  }

  static void copyAllFlags(KmEffectExpression src, KmEffectExpression dest) {
    Attributes.setNegated(dest, Attributes.isNegated(src));
    Attributes.setNullCheckPredicate(dest, Attributes.isNullCheckPredicate(src));
  }

  public static Map<String, Object> extractFlags(KmTypeAlias src) {
    Map<String, Object> map = new HashMap<>();
    map.put(ANNOTATIONS_KEY, Attributes.getHasAnnotations(src));
    map.put(VISIBILITY_KEY, Attributes.getVisibility(src));
    return map;
  }

  static void copyAllFlags(KmTypeAlias src, KmTypeAlias dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setVisibility(dest, Attributes.getVisibility(src));
  }

  public static Map<String, Object> extractFlags(KmValueParameter src) {
    Map<String, Object> map = new HashMap<>();
    map.put(ANNOTATIONS_KEY, Attributes.getHasAnnotations(src));
    map.put(DECLARES_DEFAULT_VALUE_KEY, Attributes.getDeclaresDefaultValue(src));
    map.put(CROSS_INLINE_KEY, Attributes.isCrossinline(src));
    map.put(NO_INLINE_KEY, Attributes.isNoinline(src));
    return map;
  }

  static void copyAllFlags(KmValueParameter src, KmValueParameter dest) {
    Attributes.setHasAnnotations(dest, Attributes.getHasAnnotations(src));
    Attributes.setDeclaresDefaultValue(dest, Attributes.getDeclaresDefaultValue(src));
    Attributes.setCrossinline(dest, Attributes.isCrossinline(src));
    Attributes.setNoinline(dest, Attributes.isNoinline(src));
  }

  public static Map<String, Object> extractFlags(KmTypeParameter src) {
    Map<String, Object> map = new HashMap<>();
    map.put(REIFIED_KEY, Attributes.isReified(src));
    return map;
  }

  static void copyAllFlags(KmTypeParameter src, KmTypeParameter dest) {
    Attributes.setReified(dest, Attributes.isReified(src));
  }
}
