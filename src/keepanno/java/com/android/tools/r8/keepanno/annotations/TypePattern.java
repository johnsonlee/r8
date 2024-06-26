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
 * A pattern structure for matching types.
 *
 * <p>If no properties are set, the default pattern matches any type.
 *
 * <p>All properties on this annotation are mutually exclusive.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface TypePattern {

  /**
   * Exact type name as a string.
   *
   * <p>For example, {@code "long"} or {@code "java.lang.String"}.
   *
   * <p>Mutually exclusive with the following other properties defining type-pattern:
   *
   * <ul>
   *   <li>constant
   *   <li>classNamePattern
   *   <li>instanceOfPattern
   * </ul>
   */
  String name() default "";

  /**
   * Exact type from a class constant.
   *
   * <p>For example, {@code String.class}.
   *
   * <p>Mutually exclusive with the following other properties defining type-pattern:
   *
   * <ul>
   *   <li>name
   *   <li>classNamePattern
   *   <li>instanceOfPattern
   * </ul>
   */
  Class<?> constant() default Object.class;

  /**
   * Classes matching the class-name pattern.
   *
   * <p>Mutually exclusive with the following other properties defining type-pattern:
   *
   * <ul>
   *   <li>name
   *   <li>constant
   *   <li>instanceOfPattern
   * </ul>
   */
  ClassNamePattern classNamePattern() default @ClassNamePattern(unqualifiedName = "");

  /**
   * Define the instance-of with a pattern.
   *
   * <p>Mutually exclusive with the following other properties defining type-pattern:
   *
   * <ul>
   *   <li>name
   *   <li>constant
   *   <li>classNamePattern
   * </ul>
   *
   * @return The pattern that defines what instance-of the class must be.
   */
  InstanceOfPattern instanceOfPattern() default @InstanceOfPattern();
}
