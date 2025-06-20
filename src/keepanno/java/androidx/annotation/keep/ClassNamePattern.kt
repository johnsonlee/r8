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
 * A pattern structure for matching names of classes and interfaces.
 *
 * <p>
 * If no properties are set, the default pattern matches any name of a class or interface.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class ClassNamePattern(

    /**
     * Define the class-name pattern by fully qualified class name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-name:
     * <ul>
     * <li>constant
     * <li>unqualifiedName
     * <li>unqualifiedNamePattern
     * <li>packageName
     * </ul>
     *
     * @return The qualified class name that defines the class.
     */
    val name: String = "",

    /**
     * Define the class-name pattern by reference to a Class constant.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-name:
     * <ul>
     * <li>name
     * <li>unqualifiedName
     * <li>unqualifiedNamePattern
     * <li>packageName
     * </ul>
     *
     * @return The class-constant that defines the class.
     */
    val constant: KClass<*> = Any::class,

    /**
     * Exact and unqualified name of the class or interface.
     *
     * <p>
     * For example, the unqualified name of {@code com.example.MyClass} is {@code MyClass}. Note
     * that for inner classes a `$` will appear in the unqualified name,such as, {@code
     * MyClass$MyInnerClass}.
     *
     * <p>
     * The default matches any unqualified name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-unqualified-name:
     * <ul>
     * <li>unqualifiedNamePattern
     * <li>name
     * <li>constant
     * </ul>
     */
    val unqualifiedName: String = "",

    /**
     * Define the unqualified class-name pattern by a string pattern.
     *
     * <p>
     * The default matches any unqualified name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-unqualified-name:
     * <ul>
     * <li>unqualifiedName
     * <li>name
     * <li>constant
     * </ul>
     *
     * @return The string pattern of the unqualified class name.
     */
    val unqualifiedNamePattern: StringPattern = StringPattern(exact = ""),

    /**
     * Exact package name of the class or interface.
     *
     * <p>
     * For example, the package of {@code com.example.MyClass} is {@code com.example}.
     *
     * <p>
     * The default matches any package.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-package-name:
     * <ul>
     * <li>name
     * <li>constant
     * </ul>
     */
    val packageName: String = "",
)
