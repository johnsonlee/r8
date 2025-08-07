// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FinishedEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.IrBasedEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.TraceInvokeEnqueuerAnalysis;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;

/** Ensure classes passed to Mockito.mock() and Mockito.spy() are not marked as "final". */
class EnqueuerMockitoAnalysis
    implements TraceInvokeEnqueuerAnalysis, IrBasedEnqueuerAnalysis, FinishedEnqueuerAnalysis {
  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Enqueuer enqueuer;

  private final DexType mockitoType;
  private final DexString mockString;
  private final DexString spyString;

  private final Set<DexProgramClass> mockedProgramClasses = Sets.newIdentityHashSet();
  private final Map<DexProgramClass, ProgramMethod> spiedInstanceTypes = Maps.newIdentityHashMap();

  public EnqueuerMockitoAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.enqueuer = enqueuer;

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    mockitoType = dexItemFactory.createType("Lorg/mockito/Mockito;");
    mockString = dexItemFactory.createString("mock");
    spyString = dexItemFactory.createString("spy");
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      EnqueuerAnalysisCollection.Builder builder) {
    EnqueuerMockitoAnalysis instance = new EnqueuerMockitoAnalysis(appView, enqueuer);
    builder.addTraceInvokeAnalysis(instance);
    builder.addIrBasedEnqueuerAnalysis(instance);
    builder.addFinishedAnalysis(instance);
  }

  private boolean isReflectiveMockInvoke(DexMethod invokedMethod) {
    return invokedMethod.getHolderType().isIdenticalTo(mockitoType)
        && (invokedMethod.getName().isIdenticalTo(mockString)
            || invokedMethod.getName().isIdenticalTo(spyString))
        && !invokedMethod.getParameters().isEmpty();
  }

  @Override
  public void traceInvokeStatic(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    if (isReflectiveMockInvoke(invokedMethod)) {
      enqueuer.getReflectiveIdentification().enqueue(context);
    }
  }

  @Override
  public boolean handleReflectiveInvoke(ProgramMethod context, InvokeMethod invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();

    if (!isReflectiveMockInvoke(invokedMethod)) {
      return false;
    }

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexType mockedType;
    if (invokedMethod.getParameter(0).isIdenticalTo(dexItemFactory.classType)) {
      // Given an explicit const-cast
      Value classValue = invoke.getFirstArgument();
      if (!classValue.isConstClass()) {
        return true;
      }
      mockedType = classValue.getDefinition().asConstClass().getType();
    } else if (invokedMethod.getParameter(invokedMethod.getArity() - 1).isArrayType()) {
      // This should always be an empty array of the mocked type.
      Value arrayValue = invoke.getLastArgument();
      ArrayTypeElement arrayType = arrayValue.getType().asArrayType();
      if (arrayType == null) {
        // Should never happen.
        return true;
      }
      ClassTypeElement memberType = arrayType.getMemberType().asClassType();
      if (memberType == null) {
        return true;
      }
      mockedType = memberType.getClassType();
    } else {
      // Should be Mockito.spy(Object).
      if (invokedMethod.getArity() != 1
          || !invokedMethod.getParameter(0).isIdenticalTo(dexItemFactory.objectType)) {
        return true;
      }
      Value objectValue = invoke.getFirstArgument();
      if (objectValue == null || objectValue.isPhi()) {
        return true;
      }
      ClassTypeElement classType = objectValue.getType().asClassType();
      if (classType == null) {
        return true;
      }
      mockedType = classType.toDexType(dexItemFactory);
      DexProgramClass spiedClass = asProgramClassOrNull(appView.definitionFor(mockedType, context));
      if (spiedClass != null) {
        spiedInstanceTypes.putIfAbsent(spiedClass, context);
      }
    }

    recordMockedType(context, mockedType);
    return true;
  }

  private void recordMockedType(ProgramMethod context, DexType mockedType) {
    DexType curType = mockedType;
    while (curType != null) {
      DexProgramClass programClass = asProgramClassOrNull(appView.definitionFor(curType, context));
      if (programClass == null) {
        return;
      }

      if (!mockedProgramClasses.add(programClass)) {
        return;
      }
      curType = programClass.getSuperType();
    }
  }

  @Override
  public void done(Enqueuer enqueuer) {
    // When Mockity.spy(instance) is used, all subtypes of the given type must be mockable.
    ImmediateAppSubtypingInfo subtypingInfo = enqueuer.getSubtypingInfo();
    ArrayDeque<DexClass> subclassDeque = new ArrayDeque<>();
    Set<DexProgramClass> seen = Sets.newIdentityHashSet();
    for (var entry : spiedInstanceTypes.entrySet()) {
      DexProgramClass spiedClass = entry.getKey();
      if (!seen.add(spiedClass)) {
        continue;
      }
      subclassDeque.addAll(subtypingInfo.getSubclasses(spiedClass));
      while (!subclassDeque.isEmpty()) {
        DexProgramClass subclass = subclassDeque.removeLast().asProgramClass();
        if (subclass == null || !seen.add(subclass)) {
          continue;
        }
        mockedProgramClasses.add(subclass);
        subclassDeque.addAll(subtypingInfo.getSubclasses(subclass));
      }
    }

    for (DexProgramClass mockedClass : mockedProgramClasses) {
      if (enqueuer.isTypeLive(mockedClass)) {
        ensureClassIsMockable(mockedClass);
      }
    }
  }

  private void ensureClassIsMockable(DexProgramClass programClass) {
    // Ensures the type is not made final so that it can still be subclassed.
    enqueuer.mutateKeepInfo(programClass, (k, c) -> k.joinClass(c, Joiner::disallowOptimization));

    // disallowOptimization --> prevent method from being marked final.
    // allowCodeReplacement --> do not inline or optimize based on method body.
    programClass.forEachProgramVirtualMethodMatching(
        enqueuer::isMethodLive,
        virtualMethod ->
            enqueuer.mutateKeepInfo(
                virtualMethod,
                (k, m) ->
                    k.joinMethod(
                        m, joiner -> joiner.disallowOptimization().allowCodeReplacement())));
  }
}
