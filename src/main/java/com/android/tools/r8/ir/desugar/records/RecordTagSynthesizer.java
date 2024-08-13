// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.MissingGlobalSyntheticsConsumerDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer.RecordClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer.RecordInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.synthetic.CallObjectInitCfCodeProvider;
import com.google.common.collect.ImmutableList;
import java.util.Collection;

public class RecordTagSynthesizer {

  static void ensureRecordClass(
      RecordInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      AppView<?> appView) {
    internalEnsureRecordClass(
        eventConsumer, null, eventConsumer, ImmutableList.of(context), appView);
  }

  static void ensureRecordClass(
      RecordClassSynthesizerDesugaringEventConsumer eventConsumer,
      Collection<DexProgramClass> recordClasses,
      AppView<?> appView) {
    internalEnsureRecordClass(eventConsumer, eventConsumer, null, recordClasses, appView);
  }

  /**
   * If java.lang.Record is referenced from a class' supertype or a program method/field signature,
   * then the global synthetic is generated upfront of the compilation to avoid confusing D8/R8.
   *
   * <p>However, if java.lang.Record is referenced only from an instruction, for example, the code
   * contains "x instance of java.lang.Record" but no record type is present, then the global
   * synthetic is generated during instruction desugaring scanning.
   */
  private static void internalEnsureRecordClass(
      RecordDesugaringEventConsumer eventConsumer,
      RecordClassSynthesizerDesugaringEventConsumer recordClassSynthesizerDesugaringEventConsumer,
      RecordInstructionDesugaringEventConsumer recordInstructionDesugaringEventConsumer,
      Collection<? extends ProgramDefinition> contexts,
      AppView<?> appView) {
    checkRecordTagNotPresent(appView);
    ensureRecordClassHelper(
        appView,
        contexts,
        eventConsumer,
        recordClassSynthesizerDesugaringEventConsumer,
        recordInstructionDesugaringEventConsumer);
  }

  public static void ensureRecordClassHelper(
      AppView<?> appView,
      Collection<? extends ProgramDefinition> contexts,
      RecordDesugaringEventConsumer eventConsumer,
      RecordClassSynthesizerDesugaringEventConsumer recordClassSynthesizerDesugaringEventConsumer,
      RecordInstructionDesugaringEventConsumer recordInstructionDesugaringEventConsumer) {
    appView
        .getSyntheticItems()
        .ensureGlobalClass(
            () -> new MissingGlobalSyntheticsConsumerDiagnostic("Record desugaring"),
            kinds -> kinds.RECORD_TAG,
            appView.dexItemFactory().recordType,
            contexts,
            appView,
            builder -> {
              DexEncodedMethod init = synthesizeRecordInitMethod(appView);
              builder.setAbstract().setDirectMethods(ImmutableList.of(init));
            },
            eventConsumer::acceptRecordClass,
            clazz -> {
              if (recordClassSynthesizerDesugaringEventConsumer != null) {
                for (ProgramDefinition context : contexts) {
                  recordClassSynthesizerDesugaringEventConsumer.acceptRecordClassContext(
                      clazz, context.asClass());
                }
              }
              if (recordInstructionDesugaringEventConsumer != null) {
                for (ProgramDefinition context : contexts) {
                  recordInstructionDesugaringEventConsumer.acceptRecordClassContext(
                      clazz, context.asMethod());
                }
              }
            });
  }

  private static void checkRecordTagNotPresent(AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    DexClass r8RecordClass =
        appView.appInfo().definitionForWithoutExistenceAssert(factory.recordTagType);
    if (r8RecordClass != null && r8RecordClass.isProgramClass()) {
      appView
          .options()
          .reporter
          .error(
              "D8/R8 is compiling a mix of desugared and non desugared input using"
                  + " java.lang.Record, but the application reader did not import correctly "
                  + factory.recordTagType);
    }
  }

  private static DexEncodedMethod synthesizeRecordInitMethod(AppView<?> appView) {
    MethodAccessFlags methodAccessFlags =
        MethodAccessFlags.fromSharedAccessFlags(
            Constants.ACC_SYNTHETIC | Constants.ACC_PROTECTED, true);
    return DexEncodedMethod.syntheticBuilder()
        .setMethod(appView.dexItemFactory().recordMembers.constructor)
        .setAccessFlags(methodAccessFlags)
        .setCode(
            new CallObjectInitCfCodeProvider(appView, appView.dexItemFactory().recordTagType)
                .generateCfCode())
        // Will be traced by the enqueuer.
        .disableAndroidApiLevelCheck()
        .build();
  }
}
