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
 * Mark that an item must be fully removed from the residual program.
 *
 * <p>Being removed from the program means that the item declaration is not present at all in the
 * residual program. For example, inlined functions are not considered removed. If content of the
 * item is allowed to be in the residual, use {@link CheckOptimizedOut}.
 *
 * <p>A class is removed if all of its members are removed and no references to the class remain.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.TYPE,
  AnnotationTarget.FIELD,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.CONSTRUCTOR,
)
public annotation class CheckRemoved(val description: String = "")
