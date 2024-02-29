// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;

public class InitClassRemover extends CodeRewriterPass<AppInfoWithClassHierarchy> {

  public InitClassRemover(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "InitClassRemover";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveInitClass();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      InitClass instruction = iterator.next().asInitClass();
      if (instruction != null) {
        DexField field = appView.initClassLens().getInitClassField(instruction.getClassValue());
        StaticGet replacement =
            StaticGet.builder()
                .setField(field)
                .setFreshOutValue(code, field.getType().toTypeElement(appView))
                .build();
        iterator.replaceCurrentInstruction(replacement);
        hasChanged = true;
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }
}
