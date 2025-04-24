// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import static com.android.tools.r8.ir.desugar.records.RecordTagSynthesizer.ensureRecordClass;

import com.android.tools.r8.contexts.CompilationContext.ClassSynthesisDesugaringContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplicationReadFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaring;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration;
import java.util.ArrayList;
import java.util.List;

public class RecordClassDesugaring implements CfClassSynthesizerDesugaring {

  private final AppView<?> appView;

  public static RecordClassDesugaring create(AppView<?> appView) {
    return appView.options().desugarRecordState().isFull()
        ? new RecordClassDesugaring(appView)
        : null;
  }

  RecordClassDesugaring(AppView<?> appView) {
    this.appView = appView;
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
    if (!flags.hasReadRecordReferenceFromProgramClass()) {
      return;
    }
    // TODO(b/413303956): Disable record desugaring in R8 of R8 partial and then remove this.
    R8PartialSubCompilationConfiguration partialCompilationConfiguration =
        appView.options().partialSubCompilationConfiguration;
    if (partialCompilationConfiguration != null
        && partialCompilationConfiguration.isR8()
        && appView.hasDefinitionFor(appView.dexItemFactory().recordType)) {
      return;
    }
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
