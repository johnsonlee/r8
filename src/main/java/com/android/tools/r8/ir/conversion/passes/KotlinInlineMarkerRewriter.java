// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;

public class KotlinInlineMarkerRewriter extends CodeRewriterPass<AppInfo> {

  private final DexType kotlinInlineMarkerType;
  private final DexType kotlinMetadataType;

  public KotlinInlineMarkerRewriter(AppView<AppInfo> appView) {
    super(appView);
    this.kotlinInlineMarkerType =
        appView.dexItemFactory().createType("Lkotlin/jvm/internal/InlineMarker;");
    this.kotlinMetadataType = appView.dexItemFactory().kotlinMetadataType;
  }

  @Override
  protected String getRewriterId() {
    return "KotlinInlineMarkerRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return options.isGeneratingDex()
        && code.context().getHolder().annotations().hasAnnotation(kotlinMetadataType);
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean changed = false;
    for (BasicBlock block : code.getBlocks()) {
      InstructionListIterator instructionIterator = block.listIterator();
      while (instructionIterator.hasNext()) {
        InvokeStatic invoke = instructionIterator.next().asInvokeStatic();
        if (invoke != null
            && invoke.getInvokedMethod().getHolderType().isIdenticalTo(kotlinInlineMarkerType)) {
          instructionIterator.removeOrReplaceByDebugLocalRead();
          changed = true;
        }
      }
    }
    if (changed) {
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(changed);
  }
}
