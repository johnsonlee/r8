// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;

public class KeepRuleExtractorOptions {

  private static final KeepRuleExtractorOptions PG_OPTIONS =
      new KeepRuleExtractorOptions(false, false);
  private static final KeepRuleExtractorOptions R8_OPTIONS =
      new KeepRuleExtractorOptions(true, true);

  public static KeepRuleExtractorOptions getPgOptions() {
    return PG_OPTIONS;
  }

  public static KeepRuleExtractorOptions getR8Options() {
    return R8_OPTIONS;
  }

  private final boolean allowCheckDiscard;
  private final boolean allowAccessModificationOption;
  private final boolean allowAnnotationRemovalOption = false;

  private KeepRuleExtractorOptions(
      boolean allowCheckDiscard, boolean allowAccessModificationOption) {
    this.allowCheckDiscard = allowCheckDiscard;
    this.allowAccessModificationOption = allowAccessModificationOption;
  }

  public boolean hasCheckDiscardSupport() {
    return allowCheckDiscard;
  }

  private boolean hasAllowAccessModificationOptionSupport() {
    return allowAccessModificationOption;
  }

  private boolean hasAllowAnnotationRemovalOptionSupport() {
    return allowAnnotationRemovalOption;
  }

  public boolean isKeepOptionSupported(KeepOption keepOption) {
    switch (keepOption) {
      case ACCESS_MODIFICATION:
        return hasAllowAccessModificationOptionSupport();
      case ANNOTATION_REMOVAL:
        return hasAllowAnnotationRemovalOptionSupport();
      default:
        return true;
    }
  }
}
