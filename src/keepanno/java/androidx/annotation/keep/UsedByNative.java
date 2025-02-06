/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See KeepItemAnnotationGenerator.java.
// ***********************************************************************************

// ***********************************************************************************
// MAINTAINED AND TESTED IN THE R8 REPO. PLEASE MAKE CHANGES THERE AND REPLICATE.
// ***********************************************************************************

package androidx.annotation.keep;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class, field or method as being accessed from native code via JNI.
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
public @interface UsedByNative {

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
   *   <li>{@link KeepItemKind#CLASS_AND_MEMBERS} otherwise.
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
   * <p>The default constraints depend on the kind of the target. For all targets the default
   * constraints include:
   *
   * <ul>
   *   <li>{@link KeepConstraint#LOOKUP}
   *   <li>{@link KeepConstraint#NAME}
   *   <li>{@link KeepConstraint#VISIBILITY_RELAX}
   * </ul>
   *
   * <p>For classes the default constraints also include:
   *
   * <ul>
   *   <li>{@link KeepConstraint#CLASS_INSTANTIATE}
   * </ul>
   *
   * <p>For methods the default constraints also include:
   *
   * <ul>
   *   <li>{@link KeepConstraint#METHOD_INVOKE}
   * </ul>
   *
   * <p>For fields the default constraints also include:
   *
   * <ul>
   *   <li>{@link KeepConstraint#FIELD_GET}
   *   <li>{@link KeepConstraint#FIELD_SET}
   * </ul>
   *
   * <p>Mutually exclusive with the property `constraintAdditions` also defining constraints.
   *
   * @return Usage constraints for the target.
   */
  KeepConstraint[] constraints() default {};

  /**
   * Add additional usage constraints of the target.
   *
   * <p>The specified constraints must remain valid for the target in addition to the default
   * constraints.
   *
   * <p>The default constraints are documented in {@link #constraints}
   *
   * <p>Mutually exclusive with the property `constraints` also defining constraints.
   *
   * @return Additional usage constraints for the target.
   */
  KeepConstraint[] constraintAdditions() default {};

  /**
   * Patterns for annotations that must remain on the item.
   *
   * <p>The annotations matching any of the patterns must remain on the item if the annotation types
   * remain in the program.
   *
   * <p>Note that if the annotation types themselves are unused/removed, then their references on
   * the item will be removed too. If the annotation types themselves are used reflectively then
   * they too need a keep annotation or rule to ensure they remain in the program.
   *
   * <p>By default no annotation patterns are defined and no annotations are required to remain.
   *
   * @return Annotation patterns
   */
  AnnotationPattern[] constrainAnnotations() default {};

  /**
   * Define the member-annotated-by pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining member-annotated-by:
   *
   * <ul>
   *   <li>memberAnnotatedByClassConstant
   *   <li>memberAnnotatedByClassNamePattern
   * </ul>
   *
   * <p>Mutually exclusive with all field and method properties as use restricts the match to both
   * types of members.
   *
   * <p>If none are specified the default is to match any member regardless of what the member is
   * annotated by.
   *
   * @return The qualified class name that defines the annotation.
   */
  String memberAnnotatedByClassName() default "";

  /**
   * Define the member-annotated-by pattern by reference to a Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining member-annotated-by:
   *
   * <ul>
   *   <li>memberAnnotatedByClassName
   *   <li>memberAnnotatedByClassNamePattern
   * </ul>
   *
   * <p>Mutually exclusive with all field and method properties as use restricts the match to both
   * types of members.
   *
   * <p>If none are specified the default is to match any member regardless of what the member is
   * annotated by.
   *
   * @return The class-constant that defines the annotation.
   */
  Class<?> memberAnnotatedByClassConstant() default Object.class;

  /**
   * Define the member-annotated-by pattern by reference to a class-name pattern.
   *
   * <p>Mutually exclusive with the following other properties defining member-annotated-by:
   *
   * <ul>
   *   <li>memberAnnotatedByClassName
   *   <li>memberAnnotatedByClassConstant
   * </ul>
   *
   * <p>Mutually exclusive with all field and method properties as use restricts the match to both
   * types of members.
   *
   * <p>If none are specified the default is to match any member regardless of what the member is
   * annotated by.
   *
   * @return The class-name pattern that defines the annotation.
   */
  ClassNamePattern memberAnnotatedByClassNamePattern() default
      @ClassNamePattern(unqualifiedName = "");

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
   * Define the method-annotated-by pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining method-annotated-by:
   *
   * <ul>
   *   <li>methodAnnotatedByClassConstant
   *   <li>methodAnnotatedByClassNamePattern
   * </ul>
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none are specified the default is to match any method regardless of what the method is
   * annotated by.
   *
   * @return The qualified class name that defines the annotation.
   */
  String methodAnnotatedByClassName() default "";

  /**
   * Define the method-annotated-by pattern by reference to a Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining method-annotated-by:
   *
   * <ul>
   *   <li>methodAnnotatedByClassName
   *   <li>methodAnnotatedByClassNamePattern
   * </ul>
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none are specified the default is to match any method regardless of what the method is
   * annotated by.
   *
   * @return The class-constant that defines the annotation.
   */
  Class<?> methodAnnotatedByClassConstant() default Object.class;

  /**
   * Define the method-annotated-by pattern by reference to a class-name pattern.
   *
   * <p>Mutually exclusive with the following other properties defining method-annotated-by:
   *
   * <ul>
   *   <li>methodAnnotatedByClassName
   *   <li>methodAnnotatedByClassConstant
   * </ul>
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none are specified the default is to match any method regardless of what the method is
   * annotated by.
   *
   * @return The class-name pattern that defines the annotation.
   */
  ClassNamePattern methodAnnotatedByClassNamePattern() default
      @ClassNamePattern(unqualifiedName = "");

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
   * <p>Mutually exclusive with the property `methodNamePattern` also defining method-name.
   *
   * @return The exact method name of the method.
   */
  String methodName() default "";

  /**
   * Define the method-name pattern by a string pattern.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any method
   * name.
   *
   * <p>Mutually exclusive with the property `methodName` also defining method-name.
   *
   * @return The string pattern of the method name.
   */
  StringPattern methodNamePattern() default @StringPattern(exact = "");

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
   * Define the field-annotated-by pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining field-annotated-by:
   *
   * <ul>
   *   <li>fieldAnnotatedByClassConstant
   *   <li>fieldAnnotatedByClassNamePattern
   * </ul>
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none are specified the default is to match any field regardless of what the field is
   * annotated by.
   *
   * @return The qualified class name that defines the annotation.
   */
  String fieldAnnotatedByClassName() default "";

  /**
   * Define the field-annotated-by pattern by reference to a Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining field-annotated-by:
   *
   * <ul>
   *   <li>fieldAnnotatedByClassName
   *   <li>fieldAnnotatedByClassNamePattern
   * </ul>
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none are specified the default is to match any field regardless of what the field is
   * annotated by.
   *
   * @return The class-constant that defines the annotation.
   */
  Class<?> fieldAnnotatedByClassConstant() default Object.class;

  /**
   * Define the field-annotated-by pattern by reference to a class-name pattern.
   *
   * <p>Mutually exclusive with the following other properties defining field-annotated-by:
   *
   * <ul>
   *   <li>fieldAnnotatedByClassName
   *   <li>fieldAnnotatedByClassConstant
   * </ul>
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none are specified the default is to match any field regardless of what the field is
   * annotated by.
   *
   * @return The class-name pattern that defines the annotation.
   */
  ClassNamePattern fieldAnnotatedByClassNamePattern() default
      @ClassNamePattern(unqualifiedName = "");

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
   * <p>Mutually exclusive with the property `fieldNamePattern` also defining field-name.
   *
   * @return The exact field name of the field.
   */
  String fieldName() default "";

  /**
   * Define the field-name pattern by a string pattern.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any field
   * name.
   *
   * <p>Mutually exclusive with the property `fieldName` also defining field-name.
   *
   * @return The string pattern of the field name.
   */
  StringPattern fieldNamePattern() default @StringPattern(exact = "");

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
