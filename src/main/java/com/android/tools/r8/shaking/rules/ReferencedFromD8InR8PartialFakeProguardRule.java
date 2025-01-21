// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.rules;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.ProguardClassType;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.ProguardKeepRuleType;

// TODO(b/323816623): Make an interface to use in the keep-reason tracking.
public class ReferencedFromD8InR8PartialFakeProguardRule extends ProguardKeepRuleBase {

  public ReferencedFromD8InR8PartialFakeProguardRule() {
    super(
        Origin.unknown(),
        Position.UNKNOWN,
        "r8-partial",
        null,
        null,
        null,
        false,
        ProguardClassType.CLASS,
        null,
        null,
        null,
        false,
        null,
        ProguardKeepRuleType.KEEP,
        null);
  }
}
