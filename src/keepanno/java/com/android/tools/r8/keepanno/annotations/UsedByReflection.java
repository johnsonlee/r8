// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See KeepItemAnnotationGenerator.java.
// ***********************************************************************************

package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class, field or method as being accessed reflectively.
 *
 * <p>Note: Before using this annotation, consider if instead you can annotate the code that is
 * doing reflection with {@link UsesReflection}. Annotating the reflecting code is generally more
 * clear and maintainable, and it also naturally gives rise to edges that describe just the
 * reflected aspects of the program. The {@link UsedByReflection} annotation is suitable for cases
 * where the reflecting code is not under user control, or in migrating away from rules.
 *
 * <p>When a class is annotated, member patterns can be used to define which members are to be kept.
 * When no member patterns are specified the default pattern is to match just the class.
 *
 * <p>When a member is annotated, the member patterns cannot be used as the annotated member itself
 * fully defines the item to be kept (i.e., itself).
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface UsedByReflection {

  /**
   * Optional description to document the reason for this annotation.
   *
   * @return The descriptive message. Defaults to no description.
   */
  String description() default "";

  /**
   * Conditions that should be satisfied for the annotation to be in effect.
   *
   * @return The list of preconditions. Defaults to no conditions, thus trivially/unconditionally
   *     satisfied.
   */
  KeepCondition[] preconditions() default {};

  /**
   * Additional targets to be kept in addition to the annotated class/members.
   *
   * @return List of additional target consequences. Defaults to no additional target consequences.
   */
  KeepTarget[] additionalTargets() default {};

  /**
   * Specify the kind of this item pattern.
   *
   * <p>If unspecified the default kind depends on the annotated item.
   *
   * <p>When annotating a class the default kind is:
   *
   * <ul>
   *   <li>{@link KeepItemKind#ONLY_CLASS} if no member patterns are defined;
   *   <li>{@link KeepItemKind#CLASS_AND_METHODS} if method patterns are defined;
   *   <li>{@link KeepItemKind#CLASS_AND_FIELDS} if field patterns are defined;
   *   <li>{@link KeepItemKind#CLASS_AND_MEMBERS}otherwise.
   * </ul>
   *
   * <p>When annotating a method the default kind is: {@link KeepItemKind#ONLY_METHODS}
   *
   * <p>When annotating a field the default kind is: {@link KeepItemKind#ONLY_FIELDS}
   *
   * <p>It is not possible to use {@link KeepItemKind#ONLY_CLASS} if annotating a member.
   *
   * @return The kind for this pattern.
   */
  KeepItemKind kind() default KeepItemKind.DEFAULT;

  /**
   * Define the usage constraints of the target.
   *
   * <p>The specified constraints must remain valid for the target.
   *
   * <p>The default constraints depend on the type of the target.
   *
   * <ul>
   *   <li>For classes, the default is {{@link KeepConstraint#LOOKUP}, {@link KeepConstraint#NAME},
   *       {@link KeepConstraint#CLASS_INSTANTIATE}}
   *   <li>For methods, the default is {{@link KeepConstraint#LOOKUP}, {@link KeepConstraint#NAME},
   *       {@link KeepConstraint#METHOD_INVOKE}}
   *   <li>For fields, the default is {{@link KeepConstraint#LOOKUP}, {@link KeepConstraint#NAME},
   *       {@link KeepConstraint#FIELD_GET}, {@link KeepConstraint#FIELD_SET}}
   * </ul>
   *
   * @return Usage constraints for the target.
   */
  KeepConstraint[] constraints() default {};

  /**
   * Define the member-access pattern by matching on access flags.
   *
   * <p>Mutually exclusive with all field and method properties as use restricts the match to both
   * types of members.
   *
   * @return The member access-flag constraints that must be met.
   */
  MemberAccessFlags[] memberAccess() default {};

  /**
   * Define the method-access pattern by matching on access flags.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any
   * method-access flags.
   *
   * @return The method access-flag constraints that must be met.
   */
  MethodAccessFlags[] methodAccess() default {};

  /**
   * Define the method-name pattern by an exact method name.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any method
   * name.
   *
   * @return The exact method name of the method.
   */
  String methodName() default "";

  /**
   * Define the method return-type pattern by a fully qualified type or 'void'.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any return
   * type.
   *
   * <p>Mutually exclusive with the following other properties defining return-type:
   *
   * <ul>
   *   <li>methodReturnTypeConstant
   *   <li>methodReturnTypePattern
   * </ul>
   *
   * @return The qualified type name of the method return type.
   */
  String methodReturnType() default "";

  /**
   * Define the method return-type pattern by a class constant.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any return
   * type.
   *
   * <p>Mutually exclusive with the following other properties defining return-type:
   *
   * <ul>
   *   <li>methodReturnType
   *   <li>methodReturnTypePattern
   * </ul>
   *
   * @return A class constant denoting the type of the method return type.
   */
  Class<?> methodReturnTypeConstant() default Object.class;

  /**
   * Define the method return-type pattern by a type pattern.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any return
   * type.
   *
   * <p>Mutually exclusive with the following other properties defining return-type:
   *
   * <ul>
   *   <li>methodReturnType
   *   <li>methodReturnTypeConstant
   * </ul>
   *
   * @return The pattern of the method return type.
   */
  TypePattern methodReturnTypePattern() default @TypePattern(name = "");

  /**
   * Define the method parameters pattern by a list of fully qualified types.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any
   * parameters.
   *
   * <p>Mutually exclusive with the property `methodParameterTypePatterns` also defining parameters.
   *
   * @return The list of qualified type names of the method parameters.
   */
  String[] methodParameters() default {""};

  /**
   * Define the method parameters pattern by a list of patterns on types.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any
   * parameters.
   *
   * <p>Mutually exclusive with the property `methodParameters` also defining parameters.
   *
   * @return The list of type patterns for the method parameters.
   */
  TypePattern[] methodParameterTypePatterns() default {@TypePattern(name = "")};

  /**
   * Define the field-access pattern by matching on access flags.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any
   * field-access flags.
   *
   * @return The field access-flag constraints that must be met.
   */
  FieldAccessFlags[] fieldAccess() default {};

  /**
   * Define the field-name pattern by an exact field name.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any field
   * name.
   *
   * @return The exact field name of the field.
   */
  String fieldName() default "";

  /**
   * Define the field-type pattern by a fully qualified type.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any type.
   *
   * <p>Mutually exclusive with the following other properties defining field-type:
   *
   * <ul>
   *   <li>fieldTypeConstant
   *   <li>fieldTypePattern
   * </ul>
   *
   * @return The qualified type name for the field type.
   */
  String fieldType() default "";

  /**
   * Define the field-type pattern by a class constant.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any type.
   *
   * <p>Mutually exclusive with the following other properties defining field-type:
   *
   * <ul>
   *   <li>fieldType
   *   <li>fieldTypePattern
   * </ul>
   *
   * @return The class constant for the field type.
   */
  Class<?> fieldTypeConstant() default Object.class;

  /**
   * Define the field-type pattern by a pattern on types.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any type.
   *
   * <p>Mutually exclusive with the following other properties defining field-type:
   *
   * <ul>
   *   <li>fieldType
   *   <li>fieldTypeConstant
   * </ul>
   *
   * @return The type pattern for the field type.
   */
  TypePattern fieldTypePattern() default @TypePattern(name = "");
}
