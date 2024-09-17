// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;

public interface EnqueuerInvokeAnalysis {

  /**
   * Each traceInvokeXX method is called when a corresponding invoke is found while tracing a live
   * method.
   */
  void traceInvokeStatic(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context);

  void traceInvokeDirect(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context);

  void traceInvokeInterface(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context);

  void traceInvokeSuper(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context);

  void traceInvokeVirtual(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context);
}
