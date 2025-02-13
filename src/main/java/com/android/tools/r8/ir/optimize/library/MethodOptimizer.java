// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Set;

public class MethodOptimizer extends StatelessLibraryMethodModelCollection {

  private final InternalOptions options;
  private final DexItemFactory dexItemFactory;
  private final DexMethod invoke;

  MethodOptimizer(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
    this.dexItemFactory = dexItemFactory;
    invoke = dexItemFactory.methodMethods.invoke;
  }

  @Override
  public DexType getType() {
    return dexItemFactory.methodType;
  }

  @Override
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod singleTargetReference = singleTarget.getReference();
    if (singleTargetReference.isIdenticalTo(this.invoke)) {
      EmptyVarargsUtil.replaceWithNullIfEmptyArray(invoke, 2, code, instructionIterator, options);
      assert instructionIterator.peekPrevious() == invoke;
    }
    return instructionIterator;
  }
}
