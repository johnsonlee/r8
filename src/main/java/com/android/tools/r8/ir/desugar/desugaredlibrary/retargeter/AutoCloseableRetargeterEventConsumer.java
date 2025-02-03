// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;

public interface AutoCloseableRetargeterEventConsumer {

  void acceptAutoCloseableDispatchMethod(ProgramMethod method, ProgramDefinition context);

  void acceptAutoCloseableForwardingMethod(ProgramMethod method, ProgramDefinition context);

  interface AutoCloseableRetargeterPostProcessingEventConsumer
      extends AutoCloseableRetargeterEventConsumer {

    void acceptAutoCloseableInterfaceInjection(DexProgramClass clazz, DexClass newInterface);
  }
}
