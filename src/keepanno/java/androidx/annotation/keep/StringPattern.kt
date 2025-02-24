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

package androidx.annotation.keep

import kotlin.annotation.Retention
import kotlin.annotation.Target

/**
 * A pattern structure for matching strings.
 *
 * <p>
 * If no properties are set, the default pattern matches any string.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class StringPattern(

  /**
   * Exact string content.
   *
   * <p>
   * For example, {@code "foo"} or {@code "java.lang.String"}.
   *
   * <p>
   * Mutually exclusive with the following other properties defining string-exact-pattern:
   * <ul>
   * <li>startsWith
   * <li>endsWith
   * </ul>
   */
  val exact: String = "",

  /**
   * Matches strings beginning with the given prefix.
   *
   * <p>
   * For example, {@code "get"} to match strings such as {@code "getMyValue"}.
   *
   * <p>
   * Mutually exclusive with the property `exact` also defining string-prefix-pattern.
   */
  val startsWith: String = "",

  /**
   * Matches strings ending with the given suffix.
   *
   * <p>
   * For example, {@code "Setter"} to match strings such as {@code "myValueSetter"}.
   *
   * <p>
   * Mutually exclusive with the property `exact` also defining string-suffix-pattern.
   */
  val endsWith: String = "",
)
