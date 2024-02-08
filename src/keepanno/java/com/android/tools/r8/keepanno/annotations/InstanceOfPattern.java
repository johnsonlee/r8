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
 * A pattern structure for matching instances of classes and interfaces.
 *
 * <p>If no properties are set, the default pattern matches any instance.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface InstanceOfPattern {

  /**
   * True if the pattern should include the directly matched classes.
   *
   * <p>If false, the pattern is exclusive and only matches classes that are strict subclasses of
   * the pattern.
   */
  boolean inclusive() default true;

  /** Instances of classes matching the class-name pattern. */
  ClassNamePattern classNamePattern() default @ClassNamePattern(simpleName = "");
}
