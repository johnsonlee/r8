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
// MAINTAINED AND TESTED IN THE R8 REPO. PLEASE MAKE CHANGES THERE AND REPLICATE.
// ***********************************************************************************

package androidx.annotation.keep

import kotlin.annotation.Retention
import kotlin.annotation.Target

/**
 * Mark that an item must be optimized out of the residual program.
 *
 * <p>An item is optimized out if its declaration is no longer present in the residual program. This
 * can happen due to many distinct optimizations. For example, it may be dead code and removed by
 * the usual shrinking. The item's declaration may also be removed if it could be inlined at all
 * usages, in which case the output may still contain a value if it was a field, or instructions if
 * it was a method.
 *
 * <p>CAUTION: Because of the dependency on shrinker internal optimizations and details such as
 * inlining vs merging, the use of this annotation is somewhat unreliable and should be used with
 * caution. In most cases it is more appropriate to use {@link CheckRemoved}.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.TYPE,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class CheckOptimizedOut(val description: String = "")
