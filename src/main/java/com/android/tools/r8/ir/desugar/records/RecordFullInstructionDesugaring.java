// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import static com.android.tools.r8.ir.desugar.records.RecordTagSynthesizer.ensureRecordClass;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;

public class RecordFullInstructionDesugaring extends RecordInstructionDesugaring {

  RecordFullInstructionDesugaring(AppView<?> appView) {
    super(appView);
  }

  @Override
  public void scan(
      ProgramMethod programMethod, CfInstructionDesugaringEventConsumer eventConsumer) {
    CfCode cfCode = programMethod.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : cfCode.getInstructions()) {
      scanInstruction(instruction, eventConsumer, programMethod);
    }
  }

  // The record rewriter scans the cf instructions to figure out if the record class needs to
  // be added in the output. the analysis cannot be done in desugarInstruction because the analysis
  // does not rewrite any instruction, and desugarInstruction is expected to rewrite at least one
  // instruction for assertions to be valid.
  private void scanInstruction(
      CfInstruction instruction,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context) {
    assert !instruction.isInitClass();
    if (instruction.isInvoke()) {
      CfInvoke cfInvoke = instruction.asInvoke();
      if (refersToRecord(cfInvoke.getMethod(), factory)) {
        ensureRecordClass(eventConsumer, context, appView);
      }
      return;
    }
    if (instruction.isFieldInstruction()) {
      CfFieldInstruction fieldInstruction = instruction.asFieldInstruction();
      if (refersToRecord(fieldInstruction.getField(), factory)) {
        ensureRecordClass(eventConsumer, context, appView);
      }
      return;
    }
    if (instruction.isTypeInstruction()) {
      CfTypeInstruction typeInstruction = instruction.asTypeInstruction();
      if (refersToRecord(typeInstruction.getType(), factory)) {
        ensureRecordClass(eventConsumer, context, appView);
      }
      return;
    }
    // TODO(b/179146128): Analyse MethodHandle and MethodType.
  }

  public static boolean refersToRecord(DexField field, DexItemFactory factory) {
    assert !refersToRecord(field.holder, factory) : "The java.lang.Record class has no fields.";
    return refersToRecord(field.type, factory);
  }

  public static boolean refersToRecord(DexMethod method, DexItemFactory factory) {
    if (refersToRecord(method.holder, factory)) {
      return true;
    }
    return refersToRecord(method.proto, factory);
  }

  private static boolean refersToRecord(DexProto proto, DexItemFactory factory) {
    if (refersToRecord(proto.returnType, factory)) {
      return true;
    }
    return refersToRecord(proto.parameters.values, factory);
  }

  private static boolean refersToRecord(DexType[] types, DexItemFactory factory) {
    for (DexType type : types) {
      if (refersToRecord(type, factory)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean refersToRecord(DexType type, DexItemFactory factory) {
    return type == factory.recordType;
  }
}
