// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Collection of extracted keep annotations.
 *
 * <p>This annotation is just a collection of the extracted annotations. It is version independent
 * and is assumed to never change. Any version specific changes are to be made within the single
 * element structure of {@link ExtractedKeepAnnotation}.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface ExtractedKeepAnnotations {
  ExtractedKeepAnnotation[] value();
}
