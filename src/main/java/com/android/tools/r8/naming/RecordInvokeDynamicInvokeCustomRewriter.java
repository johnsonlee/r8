// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.isInvokeCustomOnRecord;
import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.parseInvokeCustomOnRecord;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.desugar.records.RecordRewriter;
import com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.RecordInvokeDynamic;
import java.util.ArrayList;

/** Rewrites the record invokedynamic/invoke-custom in hashCode, equals and toString. */
public class RecordInvokeDynamicInvokeCustomRewriter
    extends CodeRewriterPass<AppInfoWithClassHierarchy> {

  private final RecordRewriter recordRewriter;

  public RecordInvokeDynamicInvokeCustomRewriter(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
    this.recordRewriter = RecordRewriter.create(appView);
  }

  @Override
  protected String getRewriterId() {
    return "RecordInvokeDynamicInvokeCustomRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return recordRewriter != null
        && code.context().getHolder().isRecord()
        && code.metadata().mayHaveInvokeCustom();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      InvokeCustom instruction = iterator.next().asInvokeCustom();
      if (instruction != null) {
        InvokeCustom replacement = rewriteRecordInvokeCustom(code, instruction);
        if (replacement != instruction) {
          iterator.replaceCurrentInstruction(replacement);
          hasChanged = true;
        }
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  // Called after final tree shaking, prune and minify field names and field values.
  public InvokeCustom rewriteRecordInvokeCustom(IRCode code, InvokeCustom invokeCustom) {
    ProgramMethod context = code.context();
    if (!isInvokeCustomOnRecord(invokeCustom, appView, context)) {
      return invokeCustom;
    }
    RecordInvokeDynamic recordInvokeDynamic =
        parseInvokeCustomOnRecord(invokeCustom, appView, context);
    DexString newFieldNames =
        recordInvokeDynamic
            .computeRecordFieldNamesComputationInfo()
            .internalComputeNameFor(
                recordInvokeDynamic.getRecordType(),
                appView,
                appView.getNamingLens());
    DexField[] newFields =
        recordRewriter.computePresentFields(appView.graphLens(), recordInvokeDynamic);
    return writeRecordInvokeCustom(
        invokeCustom, recordInvokeDynamic.withFieldNamesAndFields(newFieldNames, newFields));
  }

  private InvokeCustom writeRecordInvokeCustom(
      InvokeCustom invokeCustom, RecordInvokeDynamic recordInvokeDynamic) {
    DexItemFactory factory = appView.dexItemFactory();
    DexMethodHandle bootstrapMethod =
        factory.createMethodHandle(
            MethodHandleType.INVOKE_STATIC, factory.objectMethodsMembers.bootstrap, false, null);
    ArrayList<DexValue> bootstrapArgs = new ArrayList<>();
    bootstrapArgs.add(new DexValueType(recordInvokeDynamic.getRecordCodeType()));
    bootstrapArgs.add(new DexValueString(recordInvokeDynamic.getFieldNames()));
    for (DexField field : recordInvokeDynamic.getFields()) {
      // Rewrite using the code type of the field.
      DexField codeField =
          factory.createField(recordInvokeDynamic.getRecordCodeType(), field.type, field.name);
      bootstrapArgs.add(
          new DexValueMethodHandle(
              factory.createMethodHandle(MethodHandleType.INSTANCE_GET, codeField, false, null)));
    }
    DexCallSite callSite =
        factory.createCallSite(
            recordInvokeDynamic.getMethodName(),
            recordInvokeDynamic.getMethodProto(),
            bootstrapMethod,
            bootstrapArgs);
    DexType returnType = callSite.getMethodProto().getReturnType();
    assert returnType.isBooleanType()
        || returnType.isIntType()
        || returnType.isIdenticalTo(factory.stringType);
    assert invokeCustom.outValue().getType().equals(returnType.toTypeElement(appView));
    return new InvokeCustom(callSite, invokeCustom.outValue(), invokeCustom.arguments());
  }
}
