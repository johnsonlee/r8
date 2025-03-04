// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineTopLevelFlags;

class EmptyDesugaredLibrarySpecification extends MachineDesugaredLibrarySpecification {

  private static final EmptyDesugaredLibrarySpecification INSTANCE =
      new EmptyDesugaredLibrarySpecification();

  private EmptyDesugaredLibrarySpecification() {
    super(false, MachineTopLevelFlags.empty(), MachineRewritingFlags.builder().build());
  }

  /** Intentionally package-private, prefer {@link DesugaredLibrarySpecification#empty()}. */
  static EmptyDesugaredLibrarySpecification getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isSupported(DexReference reference) {
    return false;
  }
}
