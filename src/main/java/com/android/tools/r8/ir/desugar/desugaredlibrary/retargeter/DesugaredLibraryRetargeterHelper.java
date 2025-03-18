// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import java.util.function.Consumer;

// Used by the ListOfBackportedMethods utility.
public class DesugaredLibraryRetargeterHelper extends DesugaredLibraryRetargeter {

  public DesugaredLibraryRetargeterHelper(AppView<?> appView) {
    super(appView);
  }

  public void visit(Consumer<DexMethod> consumer) {
    staticRetarget.keySet().forEach(consumer);
    nonEmulatedVirtualRetarget.keySet().forEach(consumer);
    emulatedVirtualRetarget.keySet().forEach(consumer);
  }
}
