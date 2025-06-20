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

import kotlin.reflect.KClass

/**
 * A pattern structure for matching types.
 *
 * <p>
 * If no properties are set, the default pattern matches any type.
 *
 * <p>
 * All properties on this annotation are mutually exclusive.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class TypePattern(

    /**
     * Exact type name as a string.
     *
     * <p>
     * For example, {@code "long"} or {@code "java.lang.String"}.
     *
     * <p>
     * Mutually exclusive with the following other properties defining type-pattern:
     * <ul>
     * <li>constant
     * <li>classNamePattern
     * <li>instanceOfPattern
     * </ul>
     */
    val name: String = "",

    /**
     * Exact type from a class constant.
     *
     * <p>
     * For example, {@code String.class}.
     *
     * <p>
     * Mutually exclusive with the following other properties defining type-pattern:
     * <ul>
     * <li>name
     * <li>classNamePattern
     * <li>instanceOfPattern
     * </ul>
     */
    val constant: KClass<*> = Any::class,

    /**
     * Classes matching the class-name pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining type-pattern:
     * <ul>
     * <li>name
     * <li>constant
     * <li>instanceOfPattern
     * </ul>
     */
    val classNamePattern: ClassNamePattern = ClassNamePattern(unqualifiedName = ""),

    /**
     * Define the instance-of with a pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining type-pattern:
     * <ul>
     * <li>name
     * <li>constant
     * <li>classNamePattern
     * </ul>
     *
     * @return The pattern that defines what instance-of the class must be.
     */
    val instanceOfPattern: InstanceOfPattern = InstanceOfPattern(),
)
