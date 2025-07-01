// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.build.shrinker.r8integration.R8ResourceShrinkerState;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.google.common.collect.Iterables;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

abstract class ResourceGetOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  final DexItemFactory dexItemFactory;
  private Optional<Boolean> allowStringInlining = Optional.empty();
  private Optional<Boolean> allowColorInlining = Optional.empty();

  ResourceGetOptimizer(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  private synchronized boolean allowInliningOfGetStringCalls() {
    if (allowStringInlining.isPresent()) {
      return allowStringInlining.get();
    }
    populateAllowInlining();
    return allowStringInlining.get();
  }

  private synchronized boolean allowInliningOfGetColorCalls() {
    if (allowColorInlining.isPresent()) {
      return allowColorInlining.get();
    }
    populateAllowInlining();
    return allowColorInlining.get();
  }

  private synchronized void populateAllowInlining() {
    // TODO(b/312406163): Allow androidx classes that overwrite this, but don't change the value
    // or have side effects.
    allowStringInlining = Optional.of(true);
    allowColorInlining = Optional.of(appView.options().enableColorInlining);
    Map<DexClass, Boolean> cachedResults = new IdentityHashMap<>();
    for (DexClass clazz :
        Iterables.concat(
            appView.appInfo().classes(), appView.app().asDirect().classpathClasses())) {
      if (isSubtype(cachedResults, clazz)) {
        DexEncodedMethod dexGetStringMethodOverride =
            clazz.lookupMethod(dexItemFactory.androidGetStringProto, dexItemFactory.getStringName);
        if (dexGetStringMethodOverride != null) {
          allowStringInlining = Optional.of(false);
        }
        DexEncodedMethod dexGetColorOverride =
            clazz.lookupMethod(dexItemFactory.androidGetColorProto, dexItemFactory.getColorName);
        if (dexGetColorOverride != null) {
          allowColorInlining = Optional.of(false);
        }
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isSubtype(Map<DexClass, Boolean> cachedLookups, DexClass dexClass) {
    Boolean cachedValue = cachedLookups.get(dexClass);
    if (cachedValue != null) {
      return cachedValue;
    }
    if (dexClass.type == getType()) {
      return true;
    }
    if (dexClass.type == dexItemFactory.objectType) {
      return false;
    }

    if (dexClass.superType != null) {
      DexClass superClass = appView.definitionFor(dexClass.superType);
      if (superClass != null) {
        boolean superIsResourcesSubtype = isSubtype(cachedLookups, superClass);
        cachedLookups.put(dexClass, superIsResourcesSubtype);
        return superIsResourcesSubtype;
      }
    }
    cachedLookups.put(dexClass, false);
    return false;
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
    DexMethod targetReference = singleTarget.getReference();
    if (allowInliningOfGetStringCalls()
        && targetReference.isIdenticalTo(dexItemFactory.androidResourcesGetStringMethod)) {
      maybeInlineGetString(code, instructionIterator, invoke, affectedValues);
    }
    if (allowInliningOfGetColorCalls()
        && (targetReference.isIdenticalTo(dexItemFactory.androidResourcesGetColorMethod)
            || targetReference.isIdenticalTo(dexItemFactory.androidContextGetColorMethod))) {
      maybeInlineGetColor(code, instructionIterator, invoke);
    }
    return instructionIterator;
  }

  private void maybeInlineGetColor(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    if (invoke.isInvokeVirtual()) {
      assert invoke
          .getInvokedMethod()
          .match(dexItemFactory.androidGetColorProto, dexItemFactory.getColorName);
      assert invoke.inValues().size() == 2;
      Instruction valueDefinition = invoke.getLastArgument().definition;
      if (valueDefinition != null && valueDefinition.isResourceConstNumber()) {
        R8ResourceShrinkerState.SingleValueResource resourceValue =
            appView
                .getResourceShrinkerState()
                .getR8ResourceShrinkerModel()
                .getSingleValueResourceOrNull(valueDefinition.asResourceConstNumber().getValue());
        if (resourceValue != null) {
          assert resourceValue instanceof R8ResourceShrinkerState.ColorSingleValueResource;
          instructionIterator.replaceCurrentInstructionWithConstInt(
              code, ((R8ResourceShrinkerState.ColorSingleValueResource) resourceValue).getValue());
        }
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void maybeInlineGetString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    if (invoke.isInvokeVirtual()) {
      assert invoke.inValues().size() == 2;
      Instruction valueDefinition = invoke.getLastArgument().definition;
      if (valueDefinition != null && valueDefinition.isResourceConstNumber()) {
        R8ResourceShrinkerState.SingleValueResource resourceValue =
            appView
                .getResourceShrinkerState()
                .getR8ResourceShrinkerModel()
                .getSingleValueResourceOrNull(valueDefinition.asResourceConstNumber().getValue());
        if (resourceValue != null) {
          assert resourceValue instanceof R8ResourceShrinkerState.StringSingleValueResource;
          DexString value = dexItemFactory.createString(resourceValue.getSingleValueLiteral());
          instructionIterator.replaceCurrentInstructionWithConstString(
              appView, code, value, affectedValues);
        }
      }
    }
  }
}
