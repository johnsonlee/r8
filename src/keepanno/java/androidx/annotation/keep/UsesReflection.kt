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

/**
 * Annotation to declare the reflective usages made by a class, method or field.
 *
 * <p>
 * The annotation's 'value' is a list of targets to be kept if the annotated item is used. The
 * annotated item is a precondition for keeping any of the specified targets. Thus, if an annotated
 * method is determined to be unused by the program, the annotation itself will not be in effect and
 * the targets will not be kept (assuming nothing else is otherwise keeping them).
 *
 * <p>
 * The annotation's 'additionalPreconditions' is optional and can specify additional conditions that
 * should be satisfied for the annotation to be in effect.
 *
 * <p>
 * The translation of the {@link UsesReflection} annotation into a {@link KeepEdge} is as follows:
 *
 * <p>
 * Assume the item of the annotation is denoted by 'CTX' and referred to as its context.
 * <pre>
 * &#64;UsesReflection(value = targets, [additionalPreconditions = preconditions])
 * ==&gt;
 * &#64;KeepEdge(
 *   consequences = targets,
 *   preconditions = {createConditionFromContext(CTX)} + preconditions
 * )
 *
 * where
 *   KeepCondition createConditionFromContext(ctx) {
 *     if (ctx.isClass()) {
 *       return new KeepCondition(classTypeName = ctx.getClassTypeName());
 *     }
 *     if (ctx.isMethod()) {
 *       return new KeepCondition(
 *         classTypeName = ctx.getClassTypeName(),
 *         methodName = ctx.getMethodName(),
 *         methodReturnType = ctx.getMethodReturnType(),
 *         methodParameterTypes = ctx.getMethodParameterTypes());
 *     }
 *     if (ctx.isField()) {
 *       return new KeepCondition(
 *         classTypeName = ctx.getClassTypeName(),
 *         fieldName = ctx.getFieldName()
 *         fieldType = ctx.getFieldType());
 *     }
 *     // unreachable
 *   }
 * </pre>
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class UsesReflection(

    /**
     * Optional description to document the reason for this annotation.
     *
     * @return The descriptive message. Defaults to no description.
     */
    val description: String = "",

    /**
     * Consequences that must be kept if the annotation is in effect.
     *
     * @return The list of target consequences.
     */
    val value: Array<KeepTarget>,

    /**
     * Additional preconditions for the annotation to be in effect.
     *
     * @return The list of additional preconditions. Defaults to no additional preconditions.
     */
    val additionalPreconditions: Array<KeepCondition> = [],
)
