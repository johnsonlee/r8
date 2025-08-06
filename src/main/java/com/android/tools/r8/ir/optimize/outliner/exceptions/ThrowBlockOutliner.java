// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.exceptions;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Collection;
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
    assert supplyScannerConsumerForTesting();
    Collection<ThrowBlockOutline> outlines = scanner.getOutlines();
    scanner = null;

    // Convert LIR to DEX.
    processCallees(outlines, executorService);

    // TODO(b/434769547): Instead of unsetting the outliner here, we should compute a specification
    //  of the outlining that needs to happen and the methods that need to be reprocessed.
    appView.unsetThrowBlockOutliner();
  }

  private void processCallees(
      Collection<ThrowBlockOutline> outlines, ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodSet methodsToProcess = getMethodToReprocess(outlines);
    ThrowBlockOutlineMarkerRewriter rewriter = new ThrowBlockOutlineMarkerRewriter(appView);
    ThreadUtils.processItems(
        methodsToProcess,
        rewriter::processMethod,
        appView.options().getThreadingModule(),
        executorService);
  }

  private ProgramMethodSet getMethodToReprocess(Collection<ThrowBlockOutline> outlines) {
    ProgramMethodSet methodsToProcess = ProgramMethodSet.create();
    Set<DexMethod> seenUsers = Sets.newIdentityHashSet();
    for (ThrowBlockOutline outline : outlines) {
      for (DexMethod user : outline.getUsers()) {
        if (seenUsers.add(user)) {
          ProgramMethod methodToProcess = appView.definitionFor(user).asProgramMethod();
          methodsToProcess.add(methodToProcess);
        }
      }
    }
    return methodsToProcess;
  }

  private boolean supplyScannerConsumerForTesting() {
    Consumer<ThrowBlockOutlinerScanner> consumer =
        appView.options().getThrowBlockOutlinerOptions().scannerConsumerForTesting;
    if (consumer != null) {
      consumer.accept(scanner);
    }
    return true;
  }
}
