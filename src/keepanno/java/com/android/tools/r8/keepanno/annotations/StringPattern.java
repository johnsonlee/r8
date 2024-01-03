// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
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
 * A pattern structure for matching strings.
 *
 * <p>If no properties are set, the default pattern matches any string.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface StringPattern {

  /**
   * Exact string content.
   *
   * <p>For example, {@code "foo"} or {@code "java.lang.String"}.
   *
   * <p>Mutually exclusive with the following other properties defining string-exact-pattern:
   *
   * <ul>
   *   <li>startsWith
   *   <li>endsWith
   * </ul>
   */
  String exact() default "";

  /**
   * Matches strings beginning with the given prefix.
   *
   * <p>For example, {@code "get"} to match strings such as {@code "getMyValue"}.
   *
   * <p>Mutually exclusive with the property `exact` also defining string-prefix-pattern.
   */
  String startsWith() default "";

  /**
   * Matches strings ending with the given suffix.
   *
   * <p>For example, {@code "Setter"} to match strings such as {@code "myValueSetter"}.
   *
   * <p>Mutually exclusive with the property `exact` also defining string-suffix-pattern.
   */
  String endsWith() default "";
}
