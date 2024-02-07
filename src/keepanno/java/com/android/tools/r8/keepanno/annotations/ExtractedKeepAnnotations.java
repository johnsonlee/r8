// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface ExtractedKeepAnnotations {
  /**
   * The version of defining this extracted keep annotation.
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

  /** The extracted edges. */
  KeepEdge[] edges();
}
