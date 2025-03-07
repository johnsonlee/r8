// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.fixup.ConcurrentMethodFixup;
import com.android.tools.r8.graph.fixup.ConcurrentMethodFixup.ProgramClassFixer;
import com.android.tools.r8.graph.fixup.MethodNamingUtility;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.optimize.numberunboxer.NumberUnboxerBoxingStatusResolution.MethodBoxingStatusResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class NumberUnboxerTreeFixer implements ProgramClassFixer {

  private final AppView<AppInfoWithLiveness> appView;
  private final Map<DexMethod, MethodBoxingStatusResult> unboxingResult;
  private final Map<DexMethod, DexMethod> virtualMethodsRepresentative;

  private final NumberUnboxerLens.Builder lensBuilder = NumberUnboxerLens.builder();

  public NumberUnboxerTreeFixer(
      AppView<AppInfoWithLiveness> appView,
      Map<DexMethod, MethodBoxingStatusResult> unboxingResult,
      Map<DexMethod, DexMethod> virtualMethodsRepresentative) {
    this.appView = appView;
    this.unboxingResult = unboxingResult;
    this.virtualMethodsRepresentative = virtualMethodsRepresentative;
  }

  public NumberUnboxerLens fixupTree(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // Take strongly connected components, for each merge data from call-sites and methods and
    // unbox.
    new ConcurrentMethodFixup(appView, this)
        .fixupClassesConcurrentlyByConnectedProgramComponents(timing, executorService);
    return lensBuilder.build(appView, new NumberUnboxerRewriter(appView));
  }

  @Override
  public void fixupProgramClass(DexProgramClass clazz, MethodNamingUtility utility) {
    clazz.getMethodCollection().replaceMethods(m -> fixupEncodedMethod(m, utility));
  }

  @Override
  public boolean shouldReserveAsIfPinned(ProgramMethod method) {
    // We don't reprocess dependencies of unchanged methods so we have to maintain them
    // with the same signature.
    return !unboxingResult.containsKey(representative(method.getReference()));
  }

  private DexMethod representative(DexMethod method) {
    return virtualMethodsRepresentative.getOrDefault(method, method);
  }

  private DexEncodedMethod fixupEncodedMethod(
      DexEncodedMethod method, MethodNamingUtility utility) {
    if (!unboxingResult.containsKey(representative(method.getReference()))) {
      assert method
          .getReference()
          .isIdenticalTo(
              utility.nextUniqueMethod(
                  method, method.getProto(), appView.dexItemFactory().shortType));
      return method;
    }
    MethodBoxingStatusResult methodBoxingStatus =
        unboxingResult.get(representative(method.getReference()));
    assert !methodBoxingStatus.isNoneUnboxable();
    DexProto newProto = fixupProto(method.getProto(), methodBoxingStatus);
    DexMethod newMethod =
        utility.nextUniqueMethod(method, newProto, appView.dexItemFactory().shortType);

    RewrittenPrototypeDescription prototypeChanges = lensBuilder.move(method, newMethod);
    return method.toTypeSubstitutedMethodAsInlining(
        newMethod,
        appView.dexItemFactory(),
        builder ->
            builder
                .fixupOptimizationInfo(
                    appView, prototypeChanges.createMethodOptimizationInfoFixer())
                .setCompilationState(method.getCompilationState())
                .setIsLibraryMethodOverrideIf(
                    method.isNonPrivateVirtualMethod(), OptionalBool.FALSE));
  }

  private DexType fixupType(DexType type, boolean unbox) {
    if (!unbox) {
      return type;
    }
    DexType newType = appView.dexItemFactory().primitiveToBoxed.inverse().get(type);
    assert newType != null;
    return newType;
  }

  private DexProto fixupProto(DexProto proto, MethodBoxingStatusResult methodBoxingStatus) {
    DexType[] argTypes = proto.getParameters().values;
    DexType[] newArgTypes =
        ArrayUtils.initialize(
            new DexType[argTypes.length],
            i -> fixupType(argTypes[i], methodBoxingStatus.shouldUnboxArg(i)));
    DexType newReturnType = fixupType(proto.getReturnType(), methodBoxingStatus.shouldUnboxRet());
    DexProto newProto = appView.dexItemFactory().createProto(newReturnType, newArgTypes);
    assert newProto.isNotIdenticalTo(proto);
    return newProto;
  }
}
