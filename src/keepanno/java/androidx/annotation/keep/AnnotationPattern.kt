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

import java.lang.annotation.RetentionPolicy
import kotlin.annotation.Retention
import kotlin.annotation.Target
import kotlin.reflect.KClass

/**
 * A pattern structure for matching annotations.
 *
 * <p>
 * If no properties are set, the default pattern matches any annotation with a runtime retention
 * policy.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class AnnotationPattern(

  /**
   * Define the annotation-name pattern by fully qualified class name.
   *
   * <p>
   * Mutually exclusive with the following other properties defining annotation-name:
   * <ul>
   * <li>constant
   * <li>namePattern
   * </ul>
   *
   * <p>
   * If none are specified the default is to match any annotation name.
   *
   * @return The qualified class name that defines the annotation.
   */
  val name: String = "",

  /**
   * Define the annotation-name pattern by reference to a {@code Class} constant.
   *
   * <p>
   * Mutually exclusive with the following other properties defining annotation-name:
   * <ul>
   * <li>name
   * <li>namePattern
   * </ul>
   *
   * <p>
   * If none are specified the default is to match any annotation name.
   *
   * @return The Class constant that defines the annotation.
   */
  val constant: KClass<*> = Object::class,

  /**
   * Define the annotation-name pattern by reference to a class-name pattern.
   *
   * <p>
   * Mutually exclusive with the following other properties defining annotation-name:
   * <ul>
   * <li>name
   * <li>constant
   * </ul>
   *
   * <p>
   * If none are specified the default is to match any annotation name.
   *
   * @return The class-name pattern that defines the annotation.
   */
  val namePattern: ClassNamePattern = ClassNamePattern(unqualifiedName = ""),

  /**
   * Specify which retention policies must be set for the annotations.
   *
   * <p>
   * Matches annotations with matching retention policies
   *
   * @return Retention policies. By default {@code RetentionPolicy.RUNTIME}.
   */
  val retention: Array<RetentionPolicy> = [RetentionPolicy.RUNTIME],
)
