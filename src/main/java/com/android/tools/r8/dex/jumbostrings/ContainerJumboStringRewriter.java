// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.jumbostrings;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.ApplicationWriter.LazyDexString;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ContainerJumboStringRewriter extends JumboStringRewriter {

  public ContainerJumboStringRewriter(
      AppView<?> appView, List<LazyDexString> lazyDexStrings, List<VirtualFile> virtualFiles) {
    super(appView, lazyDexStrings, virtualFiles);
  }

  @Override
  public void processVirtualFiles(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (virtualFiles.isEmpty()) {
      return;
    }
    // Collect strings from all virtual files into the last DEX section.
    VirtualFile lastFile = virtualFiles.get(virtualFiles.size() - 1);
    List<VirtualFile> allExceptLastFile = virtualFiles.subList(0, virtualFiles.size() - 1);
    for (VirtualFile virtualFile : allExceptLastFile) {
      lastFile.indexedItems.addStrings(virtualFile.indexedItems.getStrings());
    }
    // Compute string layout and handle jumbo strings for the last DEX section.
    timing.begin("Process last virtual file");
    processVirtualFile(lastFile, timing, executorService);
    timing.end();
    // Handle jumbo strings for the remaining DEX sections using the string ids in the last DEX
    // section.
    ThreadUtils.processItemsThatMatches(
        allExceptLastFile,
        alwaysTrue(),
        (virtualFile, threadTiming) ->
            rewriteJumboStringsAndComputeDebugRepresentationWithExternalStringIds(
                virtualFile, lastFile.getObjectMapping(), threadTiming, executorService),
        appView.options(),
        executorService,
        timing,
        timing.beginMerger("Pre-write phase", executorService));
  }

  private void rewriteJumboStringsAndComputeDebugRepresentationWithExternalStringIds(
      VirtualFile virtualFile,
      ObjectToOffsetMapping mapping,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    computeOffsetMappingAndRewriteJumboStringsWithExternalStringIds(
        virtualFile, timing, mapping, executorService);
    DebugRepresentation.computeForFile(appView, virtualFile);
  }

  private void computeOffsetMappingAndRewriteJumboStringsWithExternalStringIds(
      VirtualFile virtualFile,
      Timing timing,
      ObjectToOffsetMapping mapping,
      ExecutorService executorService)
      throws ExecutionException {
    if (virtualFile.isEmpty()) {
      return;
    }
    timing.begin("Compute object offset mapping");
    virtualFile.computeMapping(appView, lazyDexStrings.size(), timing, mapping);
    timing.end();
    timing.begin("Rewrite jumbo strings");
    rewriteCodeWithJumboStrings(
        virtualFile.getObjectMapping(), virtualFile.classes(), executorService);
    timing.end();
  }
}
