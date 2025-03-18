// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.desugar.desugaredlibrary.R8LibraryDesugaringGraphLens;

// TODO(b/391572031): Apply type instruction rewriting from CfToCfDesugaredLibraryDisableDesugarer.
public class LirToLirDesugaredLibraryDisableDesugarer extends DesugaredLibraryDisableDesugarer {

  LirToLirDesugaredLibraryDisableDesugarer(AppView<?> appView) {
    super(appView);
  }

  public FieldLookupResult lookupField(
      FieldLookupResult previous, ProgramMethod context, R8LibraryDesugaringGraphLens lens) {
    if (isApplicableToContext(context)) {
      DexField retargetField = helper.rewriteField(previous.getReference(), context);
      if (retargetField != null) {
        assert !previous.hasReadCastType();
        assert !previous.hasWriteCastType();
        return FieldLookupResult.builder(lens).setReference(retargetField).build();
      }
    }
    return previous;
  }

  public MethodLookupResult lookupMethod(
      MethodLookupResult previous, ProgramMethod context, R8LibraryDesugaringGraphLens lens) {
    if (isApplicableToContext(context)) {
      boolean isInterface = previous.isInterface().toBoolean();
      DexMethod retargetMethod =
          helper.rewriteMethod(previous.getReference(), isInterface, context);
      if (retargetMethod != null) {
        return MethodLookupResult.builder(lens, lens.getPrevious())
            .setReference(retargetMethod)
            .setType(previous.getType())
            .setIsInterface(isInterface)
            .build();
      }
    }
    return previous;
  }
}
