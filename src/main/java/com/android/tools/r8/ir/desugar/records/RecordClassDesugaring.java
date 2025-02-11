// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import static com.android.tools.r8.ir.desugar.records.RecordTagSynthesizer.ensureRecordClass;

import com.android.tools.r8.contexts.CompilationContext.ClassSynthesisDesugaringContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplicationReadFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class RecordClassDesugaring
    implements CfClassSynthesizerDesugaring, CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public static RecordClassDesugaring create(AppView<?> appView) {
    return appView.options().desugarRecordState().isFull()
        ? new RecordClassDesugaring(appView)
        : null;
  }

  private RecordClassDesugaring(AppView<?> appView) {
    this.appView = appView;
    factory = appView.dexItemFactory();
  }

  @Override
  public String uniqueIdentifier() {
    return "$record";
  }

  @Override
  public void synthesizeClasses(
      ClassSynthesisDesugaringContext processingContext,
      CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    DexApplicationReadFlags flags = appView.appInfo().app().getFlags();
    if (flags.hasReadRecordReferenceFromProgramClass()) {
      List<DexProgramClass> classes = new ArrayList<>(flags.getRecordWitnesses().size());
      for (DexType recordWitness : flags.getRecordWitnesses()) {
        DexClass dexClass = appView.contextIndependentDefinitionFor(recordWitness);
        assert dexClass != null;
        assert dexClass.isProgramClass();
        classes.add(dexClass.asProgramClass());
      }
      ensureRecordClass(eventConsumer, classes, appView);
    }
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService,
      Timing timing) {
    try (Timing t0 = timing.begin("Record class desugaring")) {
      for (DexProgramClass clazz : programClasses) {
        if (clazz.isRecord()) {
          assert clazz.superType == factory.recordType;
          clazz.accessFlags.unsetRecord();
        }
      }
    }
  }
}
