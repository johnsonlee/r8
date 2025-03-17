// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockInstructionListIterator;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.LirToLirDesugaredLibraryApiConverter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.android.tools.r8.ir.optimize.CustomLensCodeRewriter;
import java.util.Collections;
import java.util.Set;

public class R8LibraryDesugaringGraphLens extends DefaultNonIdentityGraphLens {

  private final LirToLirDesugaredLibraryApiConverter desugaredLibraryAPIConverter;

  @SuppressWarnings("UnusedVariable")
  private final InterfaceMethodRewriter interfaceMethodRewriter;

  @SuppressWarnings("UnusedVariable")
  private final CfInstructionDesugaringEventConsumer eventConsumer;

  @SuppressWarnings("UnusedVariable")
  private final ProgramMethod method;

  @SuppressWarnings("UnusedVariable")
  private final MethodProcessingContext methodProcessingContext;

  public R8LibraryDesugaringGraphLens(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      LirToLirDesugaredLibraryApiConverter desugaredLibraryAPIConverter,
      InterfaceMethodRewriter interfaceMethodRewriter,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext) {
    super(appView);
    this.desugaredLibraryAPIConverter = desugaredLibraryAPIConverter;
    this.interfaceMethodRewriter = interfaceMethodRewriter;
    this.eventConsumer = eventConsumer;
    this.method = method;
    this.methodProcessingContext = methodProcessingContext;
  }

  @Override
  public boolean hasCustomLensCodeRewriter() {
    return true;
  }

  @Override
  public CustomLensCodeRewriter getCustomLensCodeRewriter() {
    return new R8LibraryDesugaringLensCodeRewriter();
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    // TODO(b/391572031): Implement field access desugaring.
    return previous;
  }

  @Override
  protected MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    // TODO(b/391572031): Implement invoke desugaring.
    assert previous.getPrototypeChanges().isEmpty();

    if (desugaredLibraryAPIConverter != null) {
      return desugaredLibraryAPIConverter.lookupMethod(
          previous, method, methodProcessingContext, this);
    }

    return previous;
  }

  private class R8LibraryDesugaringLensCodeRewriter implements CustomLensCodeRewriter {

    @Override
    public Set<Phi> rewriteCode(
        IRCode code,
        MethodProcessor methodProcessor,
        RewrittenPrototypeDescription prototypeChanges,
        NonIdentityGraphLens lens) {
      boolean changed = false;
      BasicBlockIterator blocks = code.listIterator();
      GraphLens codeLens = code.context().getDefinition().getCode().getCodeLens(appView);
      while (blocks.hasNext()) {
        BasicBlock block = blocks.next();
        BasicBlockInstructionListIterator instructions = block.listIterator();
        while (instructions.hasNext()) {
          InvokeMethod invoke = instructions.next().asInvokeMethod();
          if (invoke == null) {
            continue;
          }
          MethodLookupResult lookupResult =
              lookupMethod(
                  invoke.getInvokedMethod(),
                  code.context().getReference(),
                  invoke.getType(),
                  codeLens,
                  invoke.getInterfaceBit());
          if (lookupResult.isNeedsDesugaredLibraryApiConversionSet()) {
            rewriteInvoke(code, blocks, instructions, invoke);
          }
          changed = true;
        }
      }
      assert changed;
      return Collections.emptySet();
    }

    @SuppressWarnings("UnusedVariable")
    private void rewriteInvoke(
        IRCode code,
        BasicBlockIterator blocks,
        BasicBlockInstructionListIterator instructions,
        InvokeMethod invoke) {
      // TODO(b/): Implement IR-to-IR invoke desugaring.
    }
  }
}
