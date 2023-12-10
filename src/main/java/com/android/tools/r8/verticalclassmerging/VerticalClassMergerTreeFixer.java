// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.fixup.TreeFixerBase;
import com.android.tools.r8.shaking.AnnotationFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Timing;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

class VerticalClassMergerTreeFixer extends TreeFixerBase {

  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final VerticalClassMergerGraphLens.Builder lensBuilder;
  private final VerticallyMergedClasses mergedClasses;
  private final List<SynthesizedBridgeCode> synthesizedBridges;

  VerticalClassMergerTreeFixer(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      VerticalClassMergerResult verticalClassMergerResult) {
    super(appView);
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.lensBuilder =
        VerticalClassMergerGraphLens.Builder.createBuilderForFixup(verticalClassMergerResult);
    this.mergedClasses = verticalClassMergerResult.getVerticallyMergedClasses();
    this.synthesizedBridges = verticalClassMergerResult.getSynthesizedBridges();
  }

  VerticalClassMergerGraphLens run(
      List<Set<DexProgramClass>> connectedComponents,
      Set<DexProgramClass> singletonComponents,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Fixup");
    // Globally substitute merged class types in protos and holders.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.getMethodCollection().replaceMethods(this::fixupMethod);
      clazz.setStaticFields(fixupFields(clazz.staticFields()));
      clazz.setInstanceFields(fixupFields(clazz.instanceFields()));
      clazz.setPermittedSubclassAttributes(
          fixupPermittedSubclassAttribute(clazz.getPermittedSubclassAttributes()));
    }
    VerticalClassMergerGraphLens lens = lensBuilder.build(appView, mergedClasses);
    for (SynthesizedBridgeCode synthesizedBridge : synthesizedBridges) {
      synthesizedBridge.updateMethodSignatures(lens);
    }
    new AnnotationFixer(appView, lens).run(appView.appInfo().classes(), executorService);
    timing.end();
    return lens;
  }

  @Override
  public DexType mapClassType(DexType type) {
    while (mergedClasses.hasBeenMergedIntoSubtype(type)) {
      type = mergedClasses.getTargetFor(type);
    }
    return type;
  }

  @Override
  public void recordClassChange(DexType from, DexType to) {
    // Fixup of classes is not used so no class type should change.
    throw new Unreachable();
  }

  @Override
  public void recordFieldChange(DexField from, DexField to) {
    if (!lensBuilder.hasOriginalSignatureMappingFor(to)) {
      lensBuilder.map(from, to);
    }
  }

  @Override
  public void recordMethodChange(DexMethod from, DexMethod to) {
    if (!lensBuilder.hasOriginalSignatureMappingFor(to)) {
      lensBuilder.map(from, to).recordMove(from, to);
    }
  }

  @Override
  public DexEncodedMethod recordMethodChange(DexEncodedMethod method, DexEncodedMethod newMethod) {
    recordMethodChange(method.getReference(), newMethod.getReference());
    if (newMethod.isNonPrivateVirtualMethod()) {
      // Since we changed the return type or one of the parameters, this method cannot be a
      // classpath or library method override, since we only class merge program classes.
      assert !method.isLibraryMethodOverride().isTrue();
      newMethod.setLibraryMethodOverride(OptionalBool.FALSE);
    }
    return newMethod;
  }
}
