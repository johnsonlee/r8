// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;

public interface IrBasedEnqueuerAnalysis {

  /** Called for methods added by Enqueuer.addMethodThatRequireIrAnalysis(). */
  boolean handleReflectiveInvoke(ProgramMethod method, InvokeMethod invoke);
}
