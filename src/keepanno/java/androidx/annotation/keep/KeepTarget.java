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
 * A target for a keep edge.
 *
 * <p>The target denotes an item along with options for what to keep. An item can be:
 *
 * <ul>
 *   <li>a pattern on classes;
 *   <li>a pattern on methods; or
 *   <li>a pattern on fields.
 * </ul>
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface KeepTarget {

  /**
   * Specify the kind of this item pattern.
   *
   * <p>Possible values are:
   *
   * <ul>
   *   <li>{@link KeepItemKind#ONLY_CLASS}
   *   <li>{@link KeepItemKind#ONLY_MEMBERS}
   *   <li>{@link KeepItemKind#ONLY_METHODS}
   *   <li>{@link KeepItemKind#ONLY_FIELDS}
   *   <li>{@link KeepItemKind#CLASS_AND_MEMBERS}
   *   <li>{@link KeepItemKind#CLASS_AND_METHODS}
   *   <li>{@link KeepItemKind#CLASS_AND_FIELDS}
   * </ul>
   *
   * <p>If unspecified the default kind for an item depends on its member patterns:
   *
   * <ul>
   *   <li>{@link KeepItemKind#ONLY_CLASS} if no member patterns are defined
   *   <li>{@link KeepItemKind#ONLY_METHODS} if method patterns are defined
   *   <li>{@link KeepItemKind#ONLY_FIELDS} if field patterns are defined
   *   <li>{@link KeepItemKind#ONLY_MEMBERS} otherwise.
   * </ul>
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
   * Define the class pattern by reference to a binding.
   *
   * <p>Mutually exclusive with the following other properties defining class:
   *
   * <ul>
   *   <li>className
   *   <li>classConstant
   *   <li>classNamePattern
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>instanceOfPattern
   *   <li>classAnnotatedByClassName
   *   <li>classAnnotatedByClassConstant
   *   <li>classAnnotatedByClassNamePattern
   * </ul>
   *
   * <p>If none are specified the default is to match any class.
   *
   * @return The name of the binding that defines the class.
   */
  String classFromBinding() default "";

  /**
   * Define the class-name pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining class-name:
   *
   * <ul>
   *   <li>classConstant
   *   <li>classNamePattern
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class name.
   *
   * @return The qualified class name that defines the class.
   */
  String className() default "";

  /**
   * Define the class-name pattern by reference to a Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining class-name:
   *
   * <ul>
   *   <li>className
   *   <li>classNamePattern
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class name.
   *
   * @return The class-constant that defines the class.
   */
  Class<?> classConstant() default Object.class;

  /**
   * Define the class-name pattern by reference to a class-name pattern.
   *
   * <p>Mutually exclusive with the following other properties defining class-name:
   *
   * <ul>
   *   <li>className
   *   <li>classConstant
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class name.
   *
   * @return The class-name pattern that defines the class.
   */
  ClassNamePattern classNamePattern() default @ClassNamePattern(unqualifiedName = "");

  /**
   * Define the instance-of pattern as classes that are instances of the fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>instanceOfPattern
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The qualified class name that defines what instance-of the class must be.
   */
  String instanceOfClassName() default "";

  /**
   * Define the instance-of pattern as classes that are instances of the fully qualified class name.
   *
   * <p>The pattern is exclusive in that it does not match classes that are instances of the
   * pattern, but only those that are instances of classes that are subclasses of the pattern.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>instanceOfPattern
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The qualified class name that defines what instance-of the class must be.
   */
  String instanceOfClassNameExclusive() default "";

  /**
   * Define the instance-of pattern as classes that are instances the referenced Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstantExclusive
   *   <li>instanceOfPattern
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The class constant that defines what instance-of the class must be.
   */
  Class<?> instanceOfClassConstant() default Object.class;

  /**
   * Define the instance-of pattern as classes that are instances the referenced Class constant.
   *
   * <p>The pattern is exclusive in that it does not match classes that are instances of the
   * pattern, but only those that are instances of classes that are subclasses of the pattern.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>instanceOfPattern
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The class constant that defines what instance-of the class must be.
   */
  Class<?> instanceOfClassConstantExclusive() default Object.class;

  /**
   * Define the instance-of with a pattern.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The pattern that defines what instance-of the class must be.
   */
  InstanceOfPattern instanceOfPattern() default @InstanceOfPattern();

  /**
   * Define the class-annotated-by pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining class-annotated-by:
   *
   * <ul>
   *   <li>classAnnotatedByClassConstant
   *   <li>classAnnotatedByClassNamePattern
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class regardless of what the class is
   * annotated by.
   *
   * @return The qualified class name that defines the annotation.
   */
  String classAnnotatedByClassName() default "";

  /**
   * Define the class-annotated-by pattern by reference to a Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining class-annotated-by:
   *
   * <ul>
   *   <li>classAnnotatedByClassName
   *   <li>classAnnotatedByClassNamePattern
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class regardless of what the class is
   * annotated by.
   *
   * @return The class-constant that defines the annotation.
   */
  Class<?> classAnnotatedByClassConstant() default Object.class;

  /**
   * Define the class-annotated-by pattern by reference to a class-name pattern.
   *
   * <p>Mutually exclusive with the following other properties defining class-annotated-by:
   *
   * <ul>
   *   <li>classAnnotatedByClassName
   *   <li>classAnnotatedByClassConstant
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class regardless of what the class is
   * annotated by.
   *
   * @return The class-name pattern that defines the annotation.
   */
  ClassNamePattern classAnnotatedByClassNamePattern() default
      @ClassNamePattern(unqualifiedName = "");

  /**
   * Define the member pattern in full by a reference to a binding.
   *
   * <p>Mutually exclusive with all other class and member pattern properties. When a member binding
   * is referenced this item is defined to be that item, including its class and member patterns.
   *
   * @return The binding name that defines the member.
   */
  String memberFromBinding() default "";

  /**
   * Define the member-annotated-by pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining member-annotated-by:
   *
   * <ul>
   *   <li>memberAnnotatedByClassConstant
   *   <li>memberAnnotatedByClassNamePattern
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   * <p>Mutually exclusive with the property `memberFromBinding` also defining member-access.
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   * <p>Mutually exclusive with the property `memberFromBinding` also defining method-access.
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
   * <p>Mutually exclusive with the following other properties defining method-name:
   *
   * <ul>
   *   <li>methodNamePattern
   *   <li>memberFromBinding
   * </ul>
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
   * <p>Mutually exclusive with the following other properties defining method-name:
   *
   * <ul>
   *   <li>methodName
   *   <li>memberFromBinding
   * </ul>
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   * <p>Mutually exclusive with the following other properties defining parameters:
   *
   * <ul>
   *   <li>methodParameterTypePatterns
   *   <li>memberFromBinding
   * </ul>
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
   * <p>Mutually exclusive with the following other properties defining parameters:
   *
   * <ul>
   *   <li>methodParameters
   *   <li>memberFromBinding
   * </ul>
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   * <p>Mutually exclusive with the property `memberFromBinding` also defining field-access.
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
   * <p>Mutually exclusive with the following other properties defining field-name:
   *
   * <ul>
   *   <li>fieldNamePattern
   *   <li>memberFromBinding
   * </ul>
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
   * <p>Mutually exclusive with the following other properties defining field-name:
   *
   * <ul>
   *   <li>fieldName
   *   <li>memberFromBinding
   * </ul>
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
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
   *   <li>memberFromBinding
   * </ul>
   *
   * @return The type pattern for the field type.
   */
  TypePattern fieldTypePattern() default @TypePattern(name = "");
}
