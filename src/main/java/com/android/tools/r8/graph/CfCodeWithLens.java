// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.lens.GraphLens;
import java.util.List;

public class CfCodeWithLens extends CfCode {

  private GraphLens codeLens;

  public CfCodeWithLens(GraphLens codeLens, CfCode code) {
    super(code);
    this.codeLens = codeLens;
  }

  public CfCodeWithLens(
      GraphLens codeLens,
      DexType originalHolder,
      int maxStack,
      int maxLocals,
      List<CfInstruction> instructions) {
    super(originalHolder, maxStack, maxLocals, instructions);
    this.codeLens = codeLens;
  }

  @Override
  public boolean hasExplicitCodeLens() {
    return true;
  }

  @Override
  public GraphLens getCodeLens(AppView<?> appView) {
    assert codeLens != null;
    assert !codeLens.isIdentityLens();
    return codeLens;
  }

  public void setCodeLens(GraphLens codeLens) {
    this.codeLens = codeLens;
  }

  @Override
  public CfCodeWithLens getCodeAsInlining(
      DexMethod caller,
      boolean isCallerD8R8Synthesized,
      DexMethod callee,
      boolean isCalleeD8R8Synthesized,
      DexItemFactory factory) {
    CfCode code =
        super.getCodeAsInlining(
            caller, isCallerD8R8Synthesized, callee, isCalleeD8R8Synthesized, factory);
    return new CfCodeWithLens(codeLens, code);
  }
}
