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
 * A binding of a keep item.
 *
 * <p>
 * Bindings allow referencing the exact instance of a match from a condition in other conditions
 * and/or targets. It can also be used to reduce duplication of targets by sharing patterns.
 *
 * <p>
 * An item can be:
 * <ul>
 * <li>a pattern on classes;
 * <li>a pattern on methods; or
 * <li>a pattern on fields.
 * </ul>
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class KeepBinding(

    /**
     * Name with which other bindings, conditions or targets can reference the bound item pattern.
     *
     * @return Name of the binding.
     */
    val bindingName: String,

    /**
     * Specify the kind of this item pattern.
     *
     * <p>
     * Possible values are:
     * <ul>
     * <li>{@link KeepItemKind#ONLY_CLASS}
     * <li>{@link KeepItemKind#ONLY_MEMBERS}
     * <li>{@link KeepItemKind#ONLY_METHODS}
     * <li>{@link KeepItemKind#ONLY_FIELDS}
     * <li>{@link KeepItemKind#CLASS_AND_MEMBERS}
     * <li>{@link KeepItemKind#CLASS_AND_METHODS}
     * <li>{@link KeepItemKind#CLASS_AND_FIELDS}
     * </ul>
     *
     * <p>
     * If unspecified the default kind for an item depends on its member patterns:
     * <ul>
     * <li>{@link KeepItemKind#ONLY_CLASS} if no member patterns are defined
     * <li>{@link KeepItemKind#ONLY_METHODS} if method patterns are defined
     * <li>{@link KeepItemKind#ONLY_FIELDS} if field patterns are defined
     * <li>{@link KeepItemKind#ONLY_MEMBERS} otherwise.
     * </ul>
     *
     * @return The kind for this pattern.
     */
    val kind: KeepItemKind = KeepItemKind.DEFAULT,

    /**
     * Define the class pattern by reference to a binding.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class:
     * <ul>
     * <li>className
     * <li>classConstant
     * <li>classNamePattern
     * <li>instanceOfClassName
     * <li>instanceOfClassNameExclusive
     * <li>instanceOfClassConstant
     * <li>instanceOfClassConstantExclusive
     * <li>instanceOfPattern
     * <li>classAnnotatedByClassName
     * <li>classAnnotatedByClassConstant
     * <li>classAnnotatedByClassNamePattern
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class.
     *
     * @return The name of the binding that defines the class.
     */
    val classFromBinding: String = "",

    /**
     * Define the class-name pattern by fully qualified class name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-name:
     * <ul>
     * <li>classConstant
     * <li>classNamePattern
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class name.
     *
     * @return The qualified class name that defines the class.
     */
    val className: String = "",

    /**
     * Define the class-name pattern by reference to a Class constant.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-name:
     * <ul>
     * <li>className
     * <li>classNamePattern
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class name.
     *
     * @return The class-constant that defines the class.
     */
    val classConstant: KClass<*> = Any::class,

    /**
     * Define the class-name pattern by reference to a class-name pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-name:
     * <ul>
     * <li>className
     * <li>classConstant
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class name.
     *
     * @return The class-name pattern that defines the class.
     */
    val classNamePattern: ClassNamePattern = ClassNamePattern(unqualifiedName = ""),

    /**
     * Define the instance-of pattern as classes that are instances of the fully qualified class
     * name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining instance-of:
     * <ul>
     * <li>instanceOfClassNameExclusive
     * <li>instanceOfClassConstant
     * <li>instanceOfClassConstantExclusive
     * <li>instanceOfPattern
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class instance.
     *
     * @return The qualified class name that defines what instance-of the class must be.
     */
    val instanceOfClassName: String = "",

    /**
     * Define the instance-of pattern as classes that are instances of the fully qualified class
     * name.
     *
     * <p>
     * The pattern is exclusive in that it does not match classes that are instances of the pattern,
     * but only those that are instances of classes that are subclasses of the pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining instance-of:
     * <ul>
     * <li>instanceOfClassName
     * <li>instanceOfClassConstant
     * <li>instanceOfClassConstantExclusive
     * <li>instanceOfPattern
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class instance.
     *
     * @return The qualified class name that defines what instance-of the class must be.
     */
    val instanceOfClassNameExclusive: String = "",

    /**
     * Define the instance-of pattern as classes that are instances the referenced Class constant.
     *
     * <p>
     * Mutually exclusive with the following other properties defining instance-of:
     * <ul>
     * <li>instanceOfClassName
     * <li>instanceOfClassNameExclusive
     * <li>instanceOfClassConstantExclusive
     * <li>instanceOfPattern
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class instance.
     *
     * @return The class constant that defines what instance-of the class must be.
     */
    val instanceOfClassConstant: KClass<*> = Any::class,

    /**
     * Define the instance-of pattern as classes that are instances the referenced Class constant.
     *
     * <p>
     * The pattern is exclusive in that it does not match classes that are instances of the pattern,
     * but only those that are instances of classes that are subclasses of the pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining instance-of:
     * <ul>
     * <li>instanceOfClassName
     * <li>instanceOfClassNameExclusive
     * <li>instanceOfClassConstant
     * <li>instanceOfPattern
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class instance.
     *
     * @return The class constant that defines what instance-of the class must be.
     */
    val instanceOfClassConstantExclusive: KClass<*> = Any::class,

    /**
     * Define the instance-of with a pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining instance-of:
     * <ul>
     * <li>instanceOfClassName
     * <li>instanceOfClassNameExclusive
     * <li>instanceOfClassConstant
     * <li>instanceOfClassConstantExclusive
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class instance.
     *
     * @return The pattern that defines what instance-of the class must be.
     */
    val instanceOfPattern: InstanceOfPattern = InstanceOfPattern(),

    /**
     * Define the class-annotated-by pattern by fully qualified class name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-annotated-by:
     * <ul>
     * <li>classAnnotatedByClassConstant
     * <li>classAnnotatedByClassNamePattern
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class regardless of what the class is
     * annotated by.
     *
     * @return The qualified class name that defines the annotation.
     */
    val classAnnotatedByClassName: String = "",

    /**
     * Define the class-annotated-by pattern by reference to a Class constant.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-annotated-by:
     * <ul>
     * <li>classAnnotatedByClassName
     * <li>classAnnotatedByClassNamePattern
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class regardless of what the class is
     * annotated by.
     *
     * @return The class-constant that defines the annotation.
     */
    val classAnnotatedByClassConstant: KClass<*> = Any::class,

    /**
     * Define the class-annotated-by pattern by reference to a class-name pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining class-annotated-by:
     * <ul>
     * <li>classAnnotatedByClassName
     * <li>classAnnotatedByClassConstant
     * <li>classFromBinding
     * </ul>
     *
     * <p>
     * If none are specified the default is to match any class regardless of what the class is
     * annotated by.
     *
     * @return The class-name pattern that defines the annotation.
     */
    val classAnnotatedByClassNamePattern: ClassNamePattern = ClassNamePattern(unqualifiedName = ""),

    /**
     * Define the member-annotated-by pattern by fully qualified class name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining member-annotated-by:
     * <ul>
     * <li>memberAnnotatedByClassConstant
     * <li>memberAnnotatedByClassNamePattern
     * </ul>
     *
     * <p>
     * Mutually exclusive with all field and method properties as use restricts the match to both
     * types of members.
     *
     * <p>
     * If none are specified the default is to match any member regardless of what the member is
     * annotated by.
     *
     * @return The qualified class name that defines the annotation.
     */
    val memberAnnotatedByClassName: String = "",

    /**
     * Define the member-annotated-by pattern by reference to a Class constant.
     *
     * <p>
     * Mutually exclusive with the following other properties defining member-annotated-by:
     * <ul>
     * <li>memberAnnotatedByClassName
     * <li>memberAnnotatedByClassNamePattern
     * </ul>
     *
     * <p>
     * Mutually exclusive with all field and method properties as use restricts the match to both
     * types of members.
     *
     * <p>
     * If none are specified the default is to match any member regardless of what the member is
     * annotated by.
     *
     * @return The class-constant that defines the annotation.
     */
    val memberAnnotatedByClassConstant: KClass<*> = Any::class,

    /**
     * Define the member-annotated-by pattern by reference to a class-name pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining member-annotated-by:
     * <ul>
     * <li>memberAnnotatedByClassName
     * <li>memberAnnotatedByClassConstant
     * </ul>
     *
     * <p>
     * Mutually exclusive with all field and method properties as use restricts the match to both
     * types of members.
     *
     * <p>
     * If none are specified the default is to match any member regardless of what the member is
     * annotated by.
     *
     * @return The class-name pattern that defines the annotation.
     */
    val memberAnnotatedByClassNamePattern: ClassNamePattern =
        ClassNamePattern(unqualifiedName = ""),

    /**
     * Define the member-access pattern by matching on access flags.
     *
     * <p>
     * Mutually exclusive with all field and method properties as use restricts the match to both
     * types of members.
     *
     * @return The member access-flag constraints that must be met.
     */
    val memberAccess: Array<MemberAccessFlags> = [],

    /**
     * Define the method-annotated-by pattern by fully qualified class name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining method-annotated-by:
     * <ul>
     * <li>methodAnnotatedByClassConstant
     * <li>methodAnnotatedByClassNamePattern
     * </ul>
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none are specified the default is to match any method regardless of what the method is
     * annotated by.
     *
     * @return The qualified class name that defines the annotation.
     */
    val methodAnnotatedByClassName: String = "",

    /**
     * Define the method-annotated-by pattern by reference to a Class constant.
     *
     * <p>
     * Mutually exclusive with the following other properties defining method-annotated-by:
     * <ul>
     * <li>methodAnnotatedByClassName
     * <li>methodAnnotatedByClassNamePattern
     * </ul>
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none are specified the default is to match any method regardless of what the method is
     * annotated by.
     *
     * @return The class-constant that defines the annotation.
     */
    val methodAnnotatedByClassConstant: KClass<*> = Any::class,

    /**
     * Define the method-annotated-by pattern by reference to a class-name pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining method-annotated-by:
     * <ul>
     * <li>methodAnnotatedByClassName
     * <li>methodAnnotatedByClassConstant
     * </ul>
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none are specified the default is to match any method regardless of what the method is
     * annotated by.
     *
     * @return The class-name pattern that defines the annotation.
     */
    val methodAnnotatedByClassNamePattern: ClassNamePattern =
        ClassNamePattern(unqualifiedName = ""),

    /**
     * Define the method-access pattern by matching on access flags.
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none, and other properties define this item as a method, the default matches any
     * method-access flags.
     *
     * @return The method access-flag constraints that must be met.
     */
    val methodAccess: Array<MethodAccessFlags> = [],

    /**
     * Define the method-name pattern by an exact method name.
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none, and other properties define this item as a method, the default matches any method
     * name.
     *
     * <p>
     * Mutually exclusive with the property `methodNamePattern` also defining method-name.
     *
     * @return The exact method name of the method.
     */
    val methodName: String = "",

    /**
     * Define the method-name pattern by a string pattern.
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none, and other properties define this item as a method, the default matches any method
     * name.
     *
     * <p>
     * Mutually exclusive with the property `methodName` also defining method-name.
     *
     * @return The string pattern of the method name.
     */
    val methodNamePattern: StringPattern = StringPattern(exact = ""),

    /**
     * Define the method return-type pattern by a fully qualified type or 'void'.
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none, and other properties define this item as a method, the default matches any return
     * type.
     *
     * <p>
     * Mutually exclusive with the following other properties defining return-type:
     * <ul>
     * <li>methodReturnTypeConstant
     * <li>methodReturnTypePattern
     * </ul>
     *
     * @return The qualified type name of the method return type.
     */
    val methodReturnType: String = "",

    /**
     * Define the method return-type pattern by a class constant.
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none, and other properties define this item as a method, the default matches any return
     * type.
     *
     * <p>
     * Mutually exclusive with the following other properties defining return-type:
     * <ul>
     * <li>methodReturnType
     * <li>methodReturnTypePattern
     * </ul>
     *
     * @return A class constant denoting the type of the method return type.
     */
    val methodReturnTypeConstant: KClass<*> = Any::class,

    /**
     * Define the method return-type pattern by a type pattern.
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none, and other properties define this item as a method, the default matches any return
     * type.
     *
     * <p>
     * Mutually exclusive with the following other properties defining return-type:
     * <ul>
     * <li>methodReturnType
     * <li>methodReturnTypeConstant
     * </ul>
     *
     * @return The pattern of the method return type.
     */
    val methodReturnTypePattern: TypePattern = TypePattern(name = ""),

    /**
     * Define the method parameters pattern by a list of fully qualified types.
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none, and other properties define this item as a method, the default matches any
     * parameters.
     *
     * <p>
     * Mutually exclusive with the property `methodParameterTypePatterns` also defining parameters.
     *
     * @return The list of qualified type names of the method parameters.
     */
    val methodParameters: Array<String> = [""],

    /**
     * Define the method parameters pattern by a list of patterns on types.
     *
     * <p>
     * Mutually exclusive with all field properties.
     *
     * <p>
     * If none, and other properties define this item as a method, the default matches any
     * parameters.
     *
     * <p>
     * Mutually exclusive with the property `methodParameters` also defining parameters.
     *
     * @return The list of type patterns for the method parameters.
     */
    val methodParameterTypePatterns: Array<TypePattern> = [TypePattern(name = "")],

    /**
     * Define the field-annotated-by pattern by fully qualified class name.
     *
     * <p>
     * Mutually exclusive with the following other properties defining field-annotated-by:
     * <ul>
     * <li>fieldAnnotatedByClassConstant
     * <li>fieldAnnotatedByClassNamePattern
     * </ul>
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none are specified the default is to match any field regardless of what the field is
     * annotated by.
     *
     * @return The qualified class name that defines the annotation.
     */
    val fieldAnnotatedByClassName: String = "",

    /**
     * Define the field-annotated-by pattern by reference to a Class constant.
     *
     * <p>
     * Mutually exclusive with the following other properties defining field-annotated-by:
     * <ul>
     * <li>fieldAnnotatedByClassName
     * <li>fieldAnnotatedByClassNamePattern
     * </ul>
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none are specified the default is to match any field regardless of what the field is
     * annotated by.
     *
     * @return The class-constant that defines the annotation.
     */
    val fieldAnnotatedByClassConstant: KClass<*> = Any::class,

    /**
     * Define the field-annotated-by pattern by reference to a class-name pattern.
     *
     * <p>
     * Mutually exclusive with the following other properties defining field-annotated-by:
     * <ul>
     * <li>fieldAnnotatedByClassName
     * <li>fieldAnnotatedByClassConstant
     * </ul>
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none are specified the default is to match any field regardless of what the field is
     * annotated by.
     *
     * @return The class-name pattern that defines the annotation.
     */
    val fieldAnnotatedByClassNamePattern: ClassNamePattern = ClassNamePattern(unqualifiedName = ""),

    /**
     * Define the field-access pattern by matching on access flags.
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none, and other properties define this item as a field, the default matches any
     * field-access flags.
     *
     * @return The field access-flag constraints that must be met.
     */
    val fieldAccess: Array<FieldAccessFlags> = [],

    /**
     * Define the field-name pattern by an exact field name.
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none, and other properties define this item as a field, the default matches any field
     * name.
     *
     * <p>
     * Mutually exclusive with the property `fieldNamePattern` also defining field-name.
     *
     * @return The exact field name of the field.
     */
    val fieldName: String = "",

    /**
     * Define the field-name pattern by a string pattern.
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none, and other properties define this item as a field, the default matches any field
     * name.
     *
     * <p>
     * Mutually exclusive with the property `fieldName` also defining field-name.
     *
     * @return The string pattern of the field name.
     */
    val fieldNamePattern: StringPattern = StringPattern(exact = ""),

    /**
     * Define the field-type pattern by a fully qualified type.
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none, and other properties define this item as a field, the default matches any type.
     *
     * <p>
     * Mutually exclusive with the following other properties defining field-type:
     * <ul>
     * <li>fieldTypeConstant
     * <li>fieldTypePattern
     * </ul>
     *
     * @return The qualified type name for the field type.
     */
    val fieldType: String = "",

    /**
     * Define the field-type pattern by a class constant.
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none, and other properties define this item as a field, the default matches any type.
     *
     * <p>
     * Mutually exclusive with the following other properties defining field-type:
     * <ul>
     * <li>fieldType
     * <li>fieldTypePattern
     * </ul>
     *
     * @return The class constant for the field type.
     */
    val fieldTypeConstant: KClass<*> = Any::class,

    /**
     * Define the field-type pattern by a pattern on types.
     *
     * <p>
     * Mutually exclusive with all method properties.
     *
     * <p>
     * If none, and other properties define this item as a field, the default matches any type.
     *
     * <p>
     * Mutually exclusive with the following other properties defining field-type:
     * <ul>
     * <li>fieldType
     * <li>fieldTypeConstant
     * </ul>
     *
     * @return The type pattern for the field type.
     */
    val fieldTypePattern: TypePattern = TypePattern(name = ""),
)
