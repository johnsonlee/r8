// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistryWithResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class KotlinInlineMethodRemover {

  private final AppView<AppInfo> appView;
  private final DexType kotlinInlineMarkerType;
  private final DexType kotlinMetadataType;

  public KotlinInlineMethodRemover(AppView<AppInfo> appView) {
    this.appView = appView;
    this.kotlinInlineMarkerType =
        appView.dexItemFactory().createType("Lkotlin/jvm/internal/InlineMarker;");
    this.kotlinMetadataType = appView.dexItemFactory().kotlinMetadataType;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    if (appView.options().isGeneratingDex()) {
      ThreadUtils.processItems(
          this::forEachKotlinClass,
          this::processClass,
          appView.options().getThreadingModule(),
          executorService);
    }
  }

  private void forEachKotlinClass(Consumer<DexProgramClass> consumer) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.annotations().hasAnnotation(kotlinMetadataType)) {
        consumer.accept(clazz);
      } else {
        assert verifyNoKotlinInlineFunctions(clazz);
      }
    }
  }

  private void processClass(DexProgramClass clazz) {
    Set<DexEncodedMethod> methodsToRemove = null;
    for (ProgramMethod method : clazz.programMethods()) {
      if (isKotlinInlineFunction(method)) {
        assert method.getHolder().annotations().hasAnnotation(kotlinMetadataType);
        if (methodsToRemove == null) {
          methodsToRemove = Sets.newIdentityHashSet();
        }
        methodsToRemove.add(method.getDefinition());
      }
    }
    if (methodsToRemove != null) {
      clazz.getMethodCollection().removeMethods(methodsToRemove);
    }
  }

  private boolean isKotlinInlineFunction(ProgramMethod method) {
    return method.registerCodeReferencesWithResult(
        new DefaultUseRegistryWithResult<>(appView, method, false) {

          @Override
          public void registerInvokeStatic(DexMethod method) {
            if (method.getHolderType().isIdenticalTo(kotlinInlineMarkerType)) {
              setResult(true);
            }
          }

          @Override
          public void registerInvokeSpecial(DexMethod method) {
            // Intentionally empty. Override the default use registry behavior that attempts to
            // convert this invoke-special to its corresponding dex invoke type. This is only
            // possible after desugaring.
          }
        });
  }

  private boolean verifyNoKotlinInlineFunctions(DexProgramClass clazz) {
    for (ProgramMethod method : clazz.programMethods()) {
      assert !isKotlinInlineFunction(method);
    }
    return true;
  }
}
