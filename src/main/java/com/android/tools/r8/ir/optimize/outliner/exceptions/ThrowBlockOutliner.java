// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ThrowBlockOutliner {

  private final AppView<?> appView;

  // Scans IR code during IR conversion. Responsible for computing candidate outlines.
  private ThrowBlockOutlinerScanner scanner;

  private ThrowBlockOutliner(AppView<?> appView) {
    this.appView = appView;
    this.scanner = new ThrowBlockOutlinerScanner(appView);
  }

  public static ThrowBlockOutliner create(AppView<?> appView) {
    return appView.options().getThrowBlockOutlinerOptions().isEnabled(appView)
        ? new ThrowBlockOutliner(appView)
        : null;
  }

  public void scan(IRCode code) {
    // Notify the scanner.
    if (scanner != null) {
      scanner.run(code);
    }
  }

  public void tearDownScanner(ExecutorService executorService) throws ExecutionException {
    // Unset the scanner, which is responsible for computing outline candidates.
    assert scanner != null;
    Collection<ThrowBlockOutline> outlines = scanner.getOutlines();
    scanner = null;

    // Create outlines.
    materializeOutlines(outlines, executorService);
    assert supplyOutlineConsumerForTesting(outlines);

    // Convert LIR to DEX.
    processMethods(outlines, executorService);
    appView.unsetThrowBlockOutliner();
  }

  private void materializeOutlines(
      Collection<ThrowBlockOutline> outlines, ExecutorService executorService)
      throws ExecutionException {
    // Find the outlines that we need to synthesize from each method.
    ProgramMethodMap<List<ThrowBlockOutline>> synthesizingContexts = ProgramMethodMap.create();
    for (ThrowBlockOutline outline : outlines) {
      if (outline.getUsers().size() > 1) {
        ProgramMethod synthesizingContext = outline.getSynthesizingContext(appView);
        synthesizingContexts
            .computeIfAbsent(synthesizingContext, ignoreKey(ArrayList::new))
            .add(outline);
      }
    }

    // Sort the outlines per synthesizing context so that the synthesis order is deterministic.
    // We use the constant pool index of the outline as sorting key.
    synthesizingContexts.forEach(
        (synthesizingContext, outlinesFromSynthesizingContext) -> {
          if (outlinesFromSynthesizingContext.size() == 1) {
            return;
          }
          LirConstant[] constantPool =
              synthesizingContext.getDefinition().getCode().asLirCode().getConstantPool();
          Reference2IntMap<ThrowBlockOutline> outlineConstantPoolIndices =
              new Reference2IntOpenHashMap<>();
          for (int i = 0; i < constantPool.length; i++) {
            LirConstant constant = constantPool[i];
            if (constant instanceof ThrowBlockOutline) {
              outlineConstantPoolIndices.put((ThrowBlockOutline) constant, i);
            }
          }
          assert outlinesFromSynthesizingContext.stream()
              .allMatch(outlineConstantPoolIndices::containsKey);
          ListUtils.destructiveSort(
              outlinesFromSynthesizingContext,
              Comparator.comparingInt(outlineConstantPoolIndices::getInt));
        });

    // Synthesize the outlines concurrently.
    ProcessorContext processorContext = appView.createProcessorContext();
    ThreadUtils.processMap(
        synthesizingContexts,
        (synthesizingContext, outlinesFromSynthesizingContext) -> {
          MethodProcessingContext methodProcessingContext =
              processorContext.createMethodProcessingContext(synthesizingContext);
          for (ThrowBlockOutline outline : outlinesFromSynthesizingContext) {
            outline.materialize(appView, methodProcessingContext);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private void processMethods(
      Collection<ThrowBlockOutline> outlines, ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodMap<ThrowBlockOutline> methodsToReprocess = getMethodsToReprocess(outlines);
    ThrowBlockOutlineMarkerRewriter rewriter = new ThrowBlockOutlineMarkerRewriter(appView);
    ThreadUtils.processMap(
        methodsToReprocess,
        (method, outline) -> {
          assert method.getDefinition().hasCode();
          assert method.getDefinition().getCode().isLirCode();
          LirCode<Integer> lirCode = method.getDefinition().getCode().asLirCode();
          if (outline != null) {
            rewriter.processOutlineMethod(method, lirCode, outline);
          } else {
            rewriter.processMethodWithOutlineMarkers(method, lirCode);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
  }

  private ProgramMethodMap<ThrowBlockOutline> getMethodsToReprocess(
      Collection<ThrowBlockOutline> outlines) {
    ProgramMethodMap<ThrowBlockOutline> methodsToReprocess = ProgramMethodMap.create();
    Set<DexMethod> seenUsers = Sets.newIdentityHashSet();
    for (ThrowBlockOutline outline : outlines) {
      for (DexMethod user : outline.getUsers()) {
        if (seenUsers.add(user)) {
          ProgramMethod methodToReprocess = appView.definitionFor(user).asProgramMethod();
          methodsToReprocess.put(methodToReprocess, null);
        }
      }
      if (outline.getMaterializedOutlineMethod() != null) {
        methodsToReprocess.put(outline.getMaterializedOutlineMethod(), outline);
      }
    }
    return methodsToReprocess;
  }

  private boolean supplyOutlineConsumerForTesting(Collection<ThrowBlockOutline> outlines) {
    Consumer<Collection<ThrowBlockOutline>> consumer =
        appView.options().getThrowBlockOutlinerOptions().outlineConsumerForTesting;
    if (consumer != null) {
      consumer.accept(outlines);
    }
    return true;
  }
}
