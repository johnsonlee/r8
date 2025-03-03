// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Set;

public class ClassOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final DexMethod getConstructor;
  private final DexMethod getDeclaredConstructor;
  private final DexMethod getMethod;
  private final DexMethod getDeclaredMethod;
  private final DexMethod isEnum;

  ClassOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    getConstructor = factory.classMethods.getConstructor;
    getDeclaredConstructor = factory.classMethods.getDeclaredConstructor;
    getMethod = factory.classMethods.getMethod;
    getDeclaredMethod = factory.classMethods.getDeclaredMethod;
    isEnum = factory.classMethods.isEnum;
  }

  @Override
  public DexType getType() {
    return factory.classType;
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
    if (singleTargetReference.isIdenticalTo(getConstructor)
        || singleTargetReference.isIdenticalTo(getDeclaredConstructor)
        || singleTargetReference.isIdenticalTo(getMethod)
        || singleTargetReference.isIdenticalTo(getDeclaredMethod)) {
      EmptyVarargsUtil.replaceWithNullIfEmptyArray(
          invoke, invoke.arguments().size() - 1, code, instructionIterator, appView.options());
      assert instructionIterator.peekPrevious() == invoke;
    }
    if (singleTargetReference.isIdenticalTo(isEnum)) {
      Value receiver = invoke.getFirstArgument();
      if (invoke.hasUnusedOutValue()) {
        if (receiver.getType().isDefinitelyNotNull()) {
          instructionIterator.removeOrReplaceByDebugLocalRead();
        }
        return instructionIterator;
      }
      Value aliasedReceiver = receiver.getAliasedValue();
      if (aliasedReceiver.isConstClass()) {
        ConstClass constClass = aliasedReceiver.getDefinition().asConstClass();
        // An enum must both directly extend java.lang.Enum and have the ENUM bit set;
        // classes for specialized enum constants don't do the former.
        DexClass dexClass = appView.definitionFor(constClass.getType());
        if (dexClass != null) {
          boolean isEnumResult =
              dexClass.isEnum() && dexClass.getSuperType().isIdenticalTo(factory.enumType);
          instructionIterator.replaceCurrentInstructionWithConstBoolean(code, isEnumResult);
        }
      }
    }
    return instructionIterator;
  }
}
