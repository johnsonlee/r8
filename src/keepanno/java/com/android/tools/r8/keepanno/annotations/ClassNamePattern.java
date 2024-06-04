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
   * Define the class-name pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining class-name:
   *
   * <ul>
   *   <li>constant
   *   <li>unqualifiedName
   *   <li>unqualifiedNamePattern
   *   <li>packageName
   * </ul>
   *
   * @return The qualified class name that defines the class.
   */
  String name() default "";

  /**
   * Define the class-name pattern by reference to a Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining class-name:
   *
   * <ul>
   *   <li>name
   *   <li>unqualifiedName
   *   <li>unqualifiedNamePattern
   *   <li>packageName
   * </ul>
   *
   * @return The class-constant that defines the class.
   */
  Class<?> constant() default Object.class;

  /**
   * Exact and unqualified name of the class or interface.
   *
   * <p>For example, the unqualified name of {@code com.example.MyClass} is {@code MyClass}. Note
   * that for inner classes a `$` will appear in the unqualified name,such as, {@code
   * MyClass$MyInnerClass}.
   *
   * <p>The default matches any unqualified name.
   *
   * <p>Mutually exclusive with the following other properties defining class-unqualified-name:
   *
   * <ul>
   *   <li>unqualifiedNamePattern
   *   <li>name
   *   <li>constant
   * </ul>
   */
  String unqualifiedName() default "";

  /**
   * Define the unqualified class-name pattern by a string pattern.
   *
   * <p>The default matches any unqualified name.
   *
   * <p>Mutually exclusive with the following other properties defining class-unqualified-name:
   *
   * <ul>
   *   <li>unqualifiedName
   *   <li>name
   *   <li>constant
   * </ul>
   *
   * @return The string pattern of the unqualified class name.
   */
  StringPattern unqualifiedNamePattern() default @StringPattern(exact = "");

  /**
   * Exact package name of the class or interface.
   *
   * <p>For example, the package of {@code com.example.MyClass} is {@code com.example}.
   *
   * <p>The default matches any package.
   *
   * <p>Mutually exclusive with the following other properties defining class-package-name:
   *
   * <ul>
   *   <li>name
   *   <li>constant
   * </ul>
   */
  String packageName() default "";
}
