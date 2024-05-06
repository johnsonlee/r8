// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.annotations.VisibleForTesting;

public class FieldAccessAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final FieldBitAccessAnalysis fieldBitAccessAnalysis;
  private final FieldReadForInvokeReceiverAnalysis fieldReadForInvokeReceiverAnalysis;
  private final FieldReadForWriteAnalysis fieldReadForWriteAnalysis;

  public FieldAccessAnalysis(AppView<AppInfoWithLiveness> appView) {
    InternalOptions options = appView.options();
    this.appView = appView;
    this.fieldBitAccessAnalysis =
        options.enableFieldBitAccessAnalysis ? new FieldBitAccessAnalysis() : null;
    this.fieldReadForInvokeReceiverAnalysis = new FieldReadForInvokeReceiverAnalysis(appView);
    this.fieldReadForWriteAnalysis = new FieldReadForWriteAnalysis(appView);
  }

  @VisibleForTesting
  public FieldAccessAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      FieldBitAccessAnalysis fieldBitAccessAnalysis,
      FieldReadForInvokeReceiverAnalysis fieldReadForInvokeReceiverAnalysis,
      FieldReadForWriteAnalysis fieldReadForWriteAnalysis) {
    this.appView = appView;
    this.fieldBitAccessAnalysis = fieldBitAccessAnalysis;
    this.fieldReadForInvokeReceiverAnalysis = fieldReadForInvokeReceiverAnalysis;
    this.fieldReadForWriteAnalysis = fieldReadForWriteAnalysis;
  }

  public void recordFieldAccesses(
      IRCode code,
      BytecodeMetadataProvider.Builder bytecodeMetadataProviderBuilder,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor) {
    if (!methodProcessor.isPrimaryMethodProcessor()) {
      return;
    }

    if (!code.metadata().mayHaveFieldInstruction() && !code.metadata().mayHaveNewInstance()) {
      return;
    }

    for (Instruction instruction : code.instructions()) {
      if (instruction.isFieldInstruction()) {
        FieldInstruction fieldInstruction = instruction.asFieldInstruction();
        ProgramField field =
            appView.appInfo().resolveField(fieldInstruction.getField()).getProgramField();
        if (field != null) {
          if (fieldBitAccessAnalysis != null) {
            fieldBitAccessAnalysis.recordFieldAccess(
                fieldInstruction, field.getDefinition(), feedback);
          }
          if (fieldReadForInvokeReceiverAnalysis != null) {
            fieldReadForInvokeReceiverAnalysis.recordFieldAccess(
                fieldInstruction, field, bytecodeMetadataProviderBuilder, code.context());
          }
          if (fieldReadForWriteAnalysis != null) {
            fieldReadForWriteAnalysis.recordFieldAccess(
                fieldInstruction, field, bytecodeMetadataProviderBuilder);
          }
        }
      }
    }
  }
}
