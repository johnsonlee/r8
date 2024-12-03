// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.JavaUtilCollectionsMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Set;

public class ListOptimizer extends StatelessLibraryMethodModelCollection {
  private final DexItemFactory dexItemFactory;
  private final JavaUtilCollectionsMembers collectionsMembers;
  private final DexMethod of0;

  ListOptimizer(AppView<?> appView) {
    this.dexItemFactory = appView.dexItemFactory();
    collectionsMembers = dexItemFactory.javaUtilCollectionsMembers;
    of0 = dexItemFactory.javaUtilListMembers.of0;
  }

  @Override
  public DexType getType() {
    return dexItemFactory.javaUtilListType;
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
    // Convert List.of() -> Collections.EMPTY_LIST to save on the move-return-value opcode.
    if (singleTargetReference.isIdenticalTo(of0)) {
      TypeElement outType = instructionIterator.peekPrevious().getOutType();
      if (outType == null) {
        instructionIterator.removeOrReplaceByDebugLocalRead();
      } else {
        instructionIterator.replaceCurrentInstruction(
            new StaticGet(code.createValue(outType), collectionsMembers.EMPTY_LIST));
      }
    }
    return instructionIterator;
  }
}
