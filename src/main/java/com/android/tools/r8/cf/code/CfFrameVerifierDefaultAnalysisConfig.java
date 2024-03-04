// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;

public class CfFrameVerifierDefaultAnalysisConfig implements CfAnalysisConfig {

  private final CfAssignability assignability;
  private final CfCode code;
  private final ProgramMethod method;

  CfFrameVerifierDefaultAnalysisConfig(AppView<?> appView, CfCode code, ProgramMethod method) {
    this.assignability = new CfAssignability(appView);
    this.code = code;
    this.method = method;
  }

  @Override
  public CfAssignability getAssignability() {
    return assignability;
  }

  @Override
  public DexMethod getCurrentContext() {
    return method.getReference();
  }

  @Override
  public int getMaxLocals() {
    return code.getMaxLocals();
  }

  @Override
  public int getMaxStack() {
    return code.getMaxStack();
  }

  @Override
  public boolean isImmediateSuperClassOfCurrentContext(DexType type) {
    // We perform a strict check that the given type is the same as the current holder's super
    // class.
    return type.isIdenticalTo(method.getHolder().getSuperType());
  }

  @Override
  public boolean isStrengthenFramesEnabled() {
    return false;
  }
}
