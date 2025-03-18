// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.LibraryDesugaringOptions;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import java.util.Map;

public abstract class DesugaredLibraryRetargeter {

  final AppView<?> appView;
  final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;

  private final Map<DexField, DexField> staticFieldRetarget;
  final Map<DexMethod, DexMethod> covariantRetarget;
  final Map<DexMethod, DexMethod> staticRetarget;
  final Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget;
  final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget;

  public DesugaredLibraryRetargeter(AppView<?> appView) {
    this.appView = appView;
    this.syntheticHelper = new DesugaredLibraryRetargeterSyntheticHelper(appView);
    MachineDesugaredLibrarySpecification specification =
        appView.options().getLibraryDesugaringOptions().getMachineDesugaredLibrarySpecification();
    staticFieldRetarget = specification.getStaticFieldRetarget();
    covariantRetarget = specification.getCovariantRetarget();
    staticRetarget = specification.getStaticRetarget();
    nonEmulatedVirtualRetarget = specification.getNonEmulatedVirtualRetarget();
    emulatedVirtualRetarget = specification.getEmulatedVirtualRetarget();
  }

  public static CfToCfDesugaredLibraryRetargeter createCfToCf(AppView<?> appView) {
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    if (libraryDesugaringOptions.isCfToCfLibraryDesugaringEnabled()
        && libraryDesugaringOptions.getMachineDesugaredLibrarySpecification().hasRetargeting()) {
      return new CfToCfDesugaredLibraryRetargeter(appView);
    }
    return null;
  }

  public static LirToLirDesugaredLibraryRetargeter createLirToLir(AppView<?> appView) {
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    if (libraryDesugaringOptions.isLirToLirLibraryDesugaringEnabled()
        && libraryDesugaringOptions.getMachineDesugaredLibrarySpecification().hasRetargeting()) {
      return new LirToLirDesugaredLibraryRetargeter(appView);
    }
    return null;
  }

  DexField getRetargetField(DexField field, ProgramMethod context) {
    DexEncodedField resolvedField =
        appView.appInfoForDesugaring().resolveField(field, context).getResolvedField();
    if (resolvedField != null) {
      assert resolvedField.isStatic()
          || !staticFieldRetarget.containsKey(resolvedField.getReference());
      return staticFieldRetarget.get(resolvedField.getReference());
    }
    return null;
  }
}
