// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.JavaUtilConcurrentTimeUnitMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TimeUnitOptimizer extends StatelessLibraryMethodModelCollection {
  private final DexItemFactory dexItemFactory;
  private final JavaUtilConcurrentTimeUnitMembers timeUnitMembers;
  private final Map<DexField, TimeUnit> fieldToTimeUnit;
  private final Map<DexMethod, TimeUnit> methodToTimeUnit;

  TimeUnitOptimizer(AppView<?> appView) {
    this.dexItemFactory = appView.dexItemFactory();
    timeUnitMembers = dexItemFactory.javaUtilConcurrentTimeUnitMembers;
    fieldToTimeUnit =
        ImmutableMap.<DexField, TimeUnit>builder()
            .put(timeUnitMembers.DAYS, TimeUnit.DAYS)
            .put(timeUnitMembers.HOURS, TimeUnit.HOURS)
            .put(timeUnitMembers.MINUTES, TimeUnit.MINUTES)
            .put(timeUnitMembers.SECONDS, TimeUnit.SECONDS)
            .put(timeUnitMembers.MILLISECONDS, TimeUnit.MILLISECONDS)
            .put(timeUnitMembers.MICROSECONDS, TimeUnit.MICROSECONDS)
            .put(timeUnitMembers.NANOSECONDS, TimeUnit.NANOSECONDS)
            .build();
    methodToTimeUnit =
        ImmutableMap.<DexMethod, TimeUnit>builder()
            .put(timeUnitMembers.toDays, TimeUnit.DAYS)
            .put(timeUnitMembers.toHours, TimeUnit.HOURS)
            .put(timeUnitMembers.toMinutes, TimeUnit.MINUTES)
            .put(timeUnitMembers.toSeconds, TimeUnit.SECONDS)
            .put(timeUnitMembers.toMillis, TimeUnit.MILLISECONDS)
            .put(timeUnitMembers.toMicros, TimeUnit.MICROSECONDS)
            .put(timeUnitMembers.toNanos, TimeUnit.NANOSECONDS)
            .build();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.javaUtilConcurrentTimeUnitType;
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
    if (timeUnitMembers.isConversionMethod(singleTargetReference)) {
      InvokeVirtual invokeVirtual = invoke.asInvokeVirtual();
      TimeUnit srcTimeUnit;
      TimeUnit dstTimeUnit;
      Value receiver = invokeVirtual.getReceiver();
      if (singleTargetReference.isIdenticalTo(timeUnitMembers.convert)) {
        dstTimeUnit = timeUnitFromValue(receiver);
        srcTimeUnit = timeUnitFromValue(invokeVirtual.getArgument(2));
      } else {
        // toDays(), toMillis(), etc.
        dstTimeUnit = methodToTimeUnit.get(singleTargetReference);
        srcTimeUnit = timeUnitFromValue(receiver);
      }
      Value srcValue = invoke.getSecondArgument().getAliasedValue();
      if (dstTimeUnit != null && srcTimeUnit != null) {
        optimizeConversion(code, instructionIterator, invoke, dstTimeUnit, srcTimeUnit, srcValue);
      }
    }
    return instructionIterator;
  }

  private void optimizeConversion(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      TimeUnit dstTimeUnit,
      TimeUnit srcTimeUnit,
      Value srcValue) {
    TypeElement outType = invoke.getOutType();
    if (outType == null) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
      return;
    }

    if (srcValue.isConstNumber()) {
      long srcDuration = srcValue.definition.asConstNumber().getLongValue();
      long result = dstTimeUnit.convert(srcDuration, srcTimeUnit);
      ConstNumber constNumber = code.createLongConstant(result, invoke.getLocalInfo());
      instructionIterator.replaceCurrentInstruction(constNumber);
    } else {
      boolean gettingBigger = srcTimeUnit.convert(1, dstTimeUnit) == 0;
      // No cheap way to model saturated multiply.
      if (gettingBigger) {
        return;
      }
      ConstNumber factor = code.createLongConstant(srcTimeUnit.convert(1, dstTimeUnit), null);
      factor.setPosition(invoke.getPosition());
      instructionIterator.addBeforeAndPositionAfterNewInstruction(factor);
      Value newOutValue = code.createValue(TypeElement.getLong(), invoke.getLocalInfo());
      Div newInstruction = new Div(NumericType.LONG, newOutValue, srcValue, factor.outValue());
      instructionIterator.replaceCurrentInstruction(newInstruction);
    }
  }

  private TimeUnit timeUnitFromValue(Value value) {
    value = value.getAliasedValue();
    if (value.isPhi()) {
      return null;
    }
    StaticGet staticGet = value.definition.asStaticGet();
    if (staticGet == null) {
      return null;
    }
    return fieldToTimeUnit.get(staticGet.getField());
  }
}
