// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.LibraryDesugaringOptions;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import java.util.Map;
import java.util.Set;

public abstract class DesugaredLibraryRetargeter {

  final AppView<?> appView;
  private final Set<DexType> multiDexTypes;
  final DesugaredLibraryRetargeterSyntheticHelper syntheticHelper;

  private final Map<DexField, DexField> staticFieldRetarget;
  final Map<DexMethod, DexMethod> covariantRetarget;
  final Map<DexMethod, DexMethod> staticRetarget;
  final Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget;
  final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget;

  public DesugaredLibraryRetargeter(AppView<?> appView) {
    this.appView = appView;
    this.multiDexTypes = appView.dexItemFactory().multiDexTypes;
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
    if (libraryDesugaringOptions.isCfToCfLibraryDesugaringEnabled(appView)
        && libraryDesugaringOptions.getMachineDesugaredLibrarySpecification().hasRetargeting()) {
      return new CfToCfDesugaredLibraryRetargeter(appView);
    }
    return null;
  }

  public static LirToLirDesugaredLibraryRetargeter createLirToLir(
      AppView<?> appView, CfInstructionDesugaringEventConsumer eventConsumer) {
    LibraryDesugaringOptions libraryDesugaringOptions =
        appView.options().getLibraryDesugaringOptions();
    if (libraryDesugaringOptions.isLirToLirLibraryDesugaringEnabled(appView)
        && libraryDesugaringOptions.getMachineDesugaredLibrarySpecification().hasRetargeting()) {
      return new LirToLirDesugaredLibraryRetargeter(appView, eventConsumer);
    }
    return null;
  }

  boolean isApplicableToContext(ProgramMethod context) {
    return !multiDexTypes.contains(context.getHolderType());
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

  RetargetMethodSupplier getRetargetMethodSupplier(
      DexMethod invokedMethod, InvokeType invokeType, boolean isInterface, ProgramMethod context) {
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    SingleResolutionResult<?> resolutionResult =
        appInfo.resolveMethodLegacy(invokedMethod, isInterface).asSingleResolution();
    if (resolutionResult == null) {
      return null;
    }
    DexMethod resolvedMethod = resolutionResult.getResolvedMethod().getReference();
    if (invokeType.isStatic()) {
      return ensureRetargetMethod(resolvedMethod, staticRetarget);
    }
    RetargetMethodSupplier result = computeNonStaticRetarget(resolvedMethod, false);
    if (result != null && invokeType.isSuper()) {
      DexClassAndMethod singleTarget =
          appInfo.lookupSuperTarget(invokedMethod, context, appView, appInfo);
      if (singleTarget != null) {
        assert !singleTarget.getDefinition().isStatic();
        return computeNonStaticRetarget(singleTarget.getReference(), true);
      }
    }
    return result;
  }

  private RetargetMethodSupplier computeNonStaticRetarget(
      DexMethod singleTarget, boolean superInvoke) {
    EmulatedDispatchMethodDescriptor descriptor = emulatedVirtualRetarget.get(singleTarget);
    if (descriptor != null) {
      return (eventConsumer, methodProcessingContext) ->
          superInvoke
              ? syntheticHelper.ensureForwardingMethod(descriptor, eventConsumer)
              : syntheticHelper.ensureEmulatedHolderDispatchMethod(descriptor, eventConsumer);
    }
    if (covariantRetarget.containsKey(singleTarget)) {
      return (eventConsumer, methodProcessingContext) ->
          syntheticHelper.ensureCovariantRetargetMethod(
              singleTarget,
              covariantRetarget.get(singleTarget),
              eventConsumer,
              methodProcessingContext);
    }
    return ensureRetargetMethod(singleTarget, nonEmulatedVirtualRetarget);
  }

  private RetargetMethodSupplier ensureRetargetMethod(
      DexMethod method, Map<DexMethod, DexMethod> retargetMap) {
    DexMethod retargetMethod = retargetMap.get(method);
    if (retargetMethod != null) {
      return (eventConsumer, methodProcessingContext) ->
          syntheticHelper.ensureRetargetMethod(retargetMethod, eventConsumer);
    }
    return null;
  }

  interface RetargetMethodSupplier {

    DexMethod getRetargetMethod(
        CfInstructionDesugaringEventConsumer eventConsumer,
        MethodProcessingContext methodProcessingContext);
  }
}
