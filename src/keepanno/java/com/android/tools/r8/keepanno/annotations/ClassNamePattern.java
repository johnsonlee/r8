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
 * A pattern structure for matching names of classes and interfaces.
 *
 * <p>If no properties are set, the default pattern matches any name of a class or interface.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ClassNamePattern {

  /**
   * Exact simple name of the class or interface.
   *
   * <p>For example, the simple name {@code com.example.MyClass} is {@code MyClass}.
   *
   * <p>The default matches any simple name.
   */
  String simpleName() default "";

  /**
   * Exact package name of the class or interface.
   *
   * <p>For example, the package of {@code com.example.MyClass} is {@code com.example}.
   *
   * <p>The default matches any package.
   */
  String packageName() default "";
}
