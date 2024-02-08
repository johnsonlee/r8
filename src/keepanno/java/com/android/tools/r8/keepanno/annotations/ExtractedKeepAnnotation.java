// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
@Repeatable(ExtractedKeepAnnotations.class)
public @interface ExtractedKeepAnnotation {
  /**
   * The version defining this extracted keep annotation.
   *
   * <p>Note: this version property must be the first property defined. Its content may determine
   * the subsequent parsing.
   */
  String version();

  /**
   * The context giving rise to this extracted keep annotation.
   *
   * <p>The context must be a class descriptor, method descriptor or field descriptor.
   */
  String context();

  /** The extracted edge. */
  KeepEdge edge();

  boolean isCheckRemoved() default false;

  boolean isCheckOptimizedOut() default false;
}
