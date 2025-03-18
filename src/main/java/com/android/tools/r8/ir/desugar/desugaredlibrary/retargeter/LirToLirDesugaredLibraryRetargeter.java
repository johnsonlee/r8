// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.desugar.desugaredlibrary.R8LibraryDesugaringGraphLens;

public class LirToLirDesugaredLibraryRetargeter extends DesugaredLibraryRetargeter {

  LirToLirDesugaredLibraryRetargeter(AppView<?> appView) {
    super(appView);
  }

  public FieldLookupResult lookupField(
      FieldLookupResult previous, ProgramMethod context, R8LibraryDesugaringGraphLens lens) {
    DexField retargetField = getRetargetField(previous.getReference(), context);
    if (retargetField != null) {
      assert !previous.hasReadCastType();
      assert !previous.hasWriteCastType();
      return FieldLookupResult.builder(lens)
          .setReference(retargetField)
          .setReboundReference(retargetField)
          .build();
    }
    return previous;
  }

  public MethodLookupResult lookupMethod(
      MethodLookupResult previous,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      R8LibraryDesugaringGraphLens lens) {
    // TODO(b/391572031): Implement.
    return previous;
  }
}
