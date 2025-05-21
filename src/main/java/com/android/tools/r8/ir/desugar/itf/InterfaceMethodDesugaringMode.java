// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.utils.InternalOptions;

public enum InterfaceMethodDesugaringMode {
  // Library desugaring and default/static interface method desugaring is enabled.
  ALL,
  // Library desugaring is enabled and the runtime supports default/static interface methods.
  LIBRARY_DESUGARING_N_PLUS,
  // Library desugaring is enabled and the runtime does not support default/static interface
  // methods.
  LIBRARY_DESUGARING_M_MINUS,
  // Default/static interface method desugaring is enabled and library desugaring is disabled.
  INTERFACE_METHOD_DESUGARING,
  // Desugaring is disabled.
  NONE;

  public static InterfaceMethodDesugaringMode createCfToCf(AppView<?> appView) {
    InternalOptions options = appView.options();
    return create(
        options,
        true,
        options.getLibraryDesugaringOptions().isCfToCfLibraryDesugaringEnabled(appView));
  }

  public static InterfaceMethodDesugaringMode createLirToLir(AppView<?> appView) {
    InternalOptions options = appView.options();
    return create(
        options,
        false,
        options.getLibraryDesugaringOptions().isLirToLirLibraryDesugaringEnabled(appView));
  }

  public static InterfaceMethodDesugaringMode createForInterfaceMethodDesugaringInRootSetBuilder(
      InternalOptions options) {
    assert options.isInterfaceMethodDesugaringEnabled();
    return create(options, true, false);
  }

  private static InterfaceMethodDesugaringMode create(
      InternalOptions options,
      boolean enableInterfaceMethodDesugaring,
      boolean enableLibraryDesugaring) {
    if (enableInterfaceMethodDesugaring && options.isInterfaceMethodDesugaringEnabled()) {
      if (enableLibraryDesugaring) {
        return ALL;
      } else {
        return INTERFACE_METHOD_DESUGARING;
      }
    } else if (enableLibraryDesugaring) {
      if (options.isInterfaceMethodDesugaringEnabled()) {
        return LIBRARY_DESUGARING_M_MINUS;
      } else {
        return LIBRARY_DESUGARING_N_PLUS;
      }
    }
    return NONE;
  }

  public boolean isLibraryDesugaring() {
    return this == ALL || this == LIBRARY_DESUGARING_N_PLUS || this == LIBRARY_DESUGARING_M_MINUS;
  }

  public boolean isNone() {
    return this == NONE;
  }

  public boolean isSome() {
    return !isNone();
  }
}
