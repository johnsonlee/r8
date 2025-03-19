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

/**
 * Valid matches on method access flags and their negations.
 *
 * <p>The negated elements make it easier to express the inverse as we cannot use a "not/negation"
 * operation syntactically.
 */
public enum class MethodAccessFlags {
    // General member flags.
    PUBLIC,
    NON_PUBLIC,
    PRIVATE,
    NON_PRIVATE,
    PROTECTED,
    NON_PROTECTED,
    PACKAGE_PRIVATE,
    NON_PACKAGE_PRIVATE,
    STATIC,
    NON_STATIC,
    FINAL,
    NON_FINAL,
    SYNTHETIC,
    NON_SYNTHETIC,
    // Method specific flags.
    SYNCHRONIZED,
    NON_SYNCHRONIZED,
    BRIDGE,
    NON_BRIDGE,
    // VARARGS - No PG parser support
    NATIVE,
    NON_NATIVE,
    ABSTRACT,
    NON_ABSTRACT,
    STRICT_FP,
    NON_STRICT_FP,
}
