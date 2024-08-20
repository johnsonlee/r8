// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.path.PathConstraintSupplier;
import com.android.tools.r8.ir.code.AbstractValueSupplier;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorCodeScanner;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;

public class ArgumentPropagatorCodeScannerForComposableFunctions
    extends ArgumentPropagatorCodeScanner {

  private final ComposableCallGraph callGraph;

  public ArgumentPropagatorCodeScannerForComposableFunctions(
      AppView<AppInfoWithLiveness> appView, ComposableCallGraph callGraph) {
    super(appView);
    this.callGraph = callGraph;
  }

  @Override
  public void scan(
      ProgramMethod method,
      IRCode code,
      AbstractValueSupplier abstractValueSupplier,
      PathConstraintSupplier pathConstraintSupplier,
      Timing timing) {
    new CodeScanner(abstractValueSupplier, code, method, pathConstraintSupplier).scan(timing);
  }

  @Override
  protected boolean isMethodParameterAlreadyUnknown(
      DexType staticType, MethodParameter methodParameter, ProgramMethod method) {
    // We haven't defined the virtual root mapping, so we can't tell.
    return false;
  }

  private class CodeScanner extends ArgumentPropagatorCodeScanner.CodeScanner {

    protected CodeScanner(
        AbstractValueSupplier abstractValueSupplier,
        IRCode code,
        ProgramMethod method,
        PathConstraintSupplier pathConstraintSupplier) {
      super(abstractValueSupplier, code, method, pathConstraintSupplier);
    }

    @Override
    protected void addTemporaryMethodState(
        InvokeMethod invoke, ProgramMethod resolvedMethod, Timing timing) {
      ComposableCallGraphNode node = callGraph.getNodes().get(resolvedMethod);
      if (node != null && node.isComposable()) {
        super.addTemporaryMethodState(invoke, resolvedMethod, timing);
      }
    }
  }
}
