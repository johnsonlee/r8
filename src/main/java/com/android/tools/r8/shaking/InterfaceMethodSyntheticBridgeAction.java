// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.ProgramMethod;

public class InterfaceMethodSyntheticBridgeAction {
  private final ProgramMethod methodToKeep;
  private final ProgramMethod singleTarget;
  private final ProguardConfigurationRule context;
  private final ProguardMemberRule rule;
  private final ProguardIfRulePreconditionMatch ifRulePreconditionMatch;
  private final DexClass precondition;

  InterfaceMethodSyntheticBridgeAction(
      ProgramMethod methodToKeep,
      ProgramMethod singleTarget,
      ProguardConfigurationRule context,
      ProguardMemberRule rule,
      ProguardIfRulePreconditionMatch ifRulePreconditionMatch,
      DexClass precondition) {
    this.methodToKeep = methodToKeep;
    this.singleTarget = singleTarget;
    this.context = context;
    this.rule = rule;
    this.ifRulePreconditionMatch = ifRulePreconditionMatch;
    this.precondition = precondition;
  }

  public ProgramMethod getMethodToKeep() {
    return methodToKeep;
  }

  public ProgramMethod getSingleTarget() {
    return singleTarget;
  }

  public DexClass getPrecondition() {
    return precondition;
  }

  public ProguardIfRulePreconditionMatch getIfRulePreconditionMatch() {
    return ifRulePreconditionMatch;
  }

  public ProguardMemberRule getRule() {
    return rule;
  }

  public ProguardConfigurationRule getContext() {
    return context;
  }
}
