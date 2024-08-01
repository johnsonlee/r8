// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.StringUtils;

@KeepForApi
public class EmptyMemberRulesToDefaultInitRuleConversionDiagnostic implements Diagnostic {

  private final ProguardConfigurationRule rule;

  EmptyMemberRulesToDefaultInitRuleConversionDiagnostic(ProguardConfigurationRule rule) {
    this.rule = rule;
  }

  @Override
  public Origin getOrigin() {
    return rule.getOrigin();
  }

  @Override
  public Position getPosition() {
    return rule.getPosition();
  }

  @Override
  public String getDiagnosticMessage() {
    // TODO(b/356350498): Explain how to opt-in to the new behavior when this is implemented in AGP.
    return StringUtils.joinLines(
        "The current version of R8 implicitly keeps the default constructor for Proguard"
            + " configuration rules that have no member pattern. If the following rule should"
            + " continue to keep the default constructor in the next major version of R8, then it"
            + " must be augmented with the member pattern `{ void <init>(); }` to explicitly keep"
            + " the default constructor:",
        rule.toString());
  }

  // Non-kept factory to avoid that the constructor of the diagnostic is public API.
  public static class Factory {

    public static EmptyMemberRulesToDefaultInitRuleConversionDiagnostic create(
        ProguardConfigurationRule rule) {
      return new EmptyMemberRulesToDefaultInitRuleConversionDiagnostic(rule);
    }
  }
}
