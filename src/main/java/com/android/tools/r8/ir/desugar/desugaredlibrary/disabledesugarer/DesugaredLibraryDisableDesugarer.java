// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer;


import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.Set;

/**
 * Disables the rewriting of types in specific classes declared in the desugared library
 * specification, typically classes that are used pre-native multidex.
 */
public class DesugaredLibraryDisableDesugarer {

  final AppView<?> appView;
  final DesugaredLibraryDisableDesugarerHelper helper;
  private final Set<DexType> multiDexTypes;

  DesugaredLibraryDisableDesugarer(AppView<?> appView) {
    this.appView = appView;
    this.helper = new DesugaredLibraryDisableDesugarerHelper(appView);
    this.multiDexTypes = appView.dexItemFactory().multiDexTypes;
  }

  public static CfToCfDesugaredLibraryDisableDesugarer createCfToCf(AppView<?> appView) {
    return DesugaredLibraryDisableDesugarerHelper.shouldCreate(appView)
            && appView
                .options()
                .getLibraryDesugaringOptions()
                .isCfToCfLibraryDesugaringEnabled(appView)
        ? new CfToCfDesugaredLibraryDisableDesugarer(appView)
        : null;
  }

  public static LirToLirDesugaredLibraryDisableDesugarer createLirToLir(AppView<?> appView) {
    return DesugaredLibraryDisableDesugarerHelper.shouldCreate(appView)
            && appView
                .options()
                .getLibraryDesugaringOptions()
                .isLirToLirLibraryDesugaringEnabled(appView)
        ? new LirToLirDesugaredLibraryDisableDesugarer(appView)
        : null;
  }

  boolean isApplicableToContext(ProgramMethod context) {
    return multiDexTypes.contains(context.getContextType());
  }
}
