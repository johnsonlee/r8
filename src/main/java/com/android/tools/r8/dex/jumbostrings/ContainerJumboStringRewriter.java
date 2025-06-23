// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.jumbostrings;

import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.ApplicationWriter.LazyDexString;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ContainerJumboStringRewriter extends JumboStringRewriter {

  public ContainerJumboStringRewriter(
      AppView<?> appView, List<LazyDexString> lazyDexStrings, List<VirtualFile> virtualFiles) {
    super(appView, lazyDexStrings, virtualFiles);
  }

  @Override
  public Collection<Timing> processVirtualFiles(ExecutorService executorService)
      throws ExecutionException {
    if (virtualFiles.isEmpty()) {
      return new ArrayList<>();
    }
    // Collect strings from all virtual files into the last DEX section.
    VirtualFile lastFile = virtualFiles.get(virtualFiles.size() - 1);
    List<VirtualFile> allExceptLastFile = virtualFiles.subList(0, virtualFiles.size() - 1);
    for (VirtualFile virtualFile : allExceptLastFile) {
      lastFile.indexedItems.addStrings(virtualFile.indexedItems.getStrings());
    }
    Collection<Timing> timings = new ArrayList<>(virtualFiles.size());
    // Compute string layout and handle jumbo strings for the last DEX section.
    timings.add(processVirtualFile(lastFile));
    // Handle jumbo strings for the remaining DEX sections using the string ids in the last DEX
    // section.
    timings.addAll(
        ThreadUtils.processItemsWithResults(
            allExceptLastFile,
            virtualFile ->
                rewriteJumboStringsAndComputeDebugRepresentationWithExternalStringIds(
                    virtualFile, lastFile.getObjectMapping()),
            appView.options().getThreadingModule(),
            executorService));
    return timings;
  }

  private Timing rewriteJumboStringsAndComputeDebugRepresentationWithExternalStringIds(
      VirtualFile virtualFile, ObjectToOffsetMapping mapping) {
    Timing fileTiming = Timing.create("VirtualFile " + virtualFile.getId(), options);
    computeOffsetMappingAndRewriteJumboStringsWithExternalStringIds(
        virtualFile, fileTiming, mapping);
    DebugRepresentation.computeForFile(appView, virtualFile);
    fileTiming.end();
    return fileTiming;
  }

  private void computeOffsetMappingAndRewriteJumboStringsWithExternalStringIds(
      VirtualFile virtualFile, Timing timing, ObjectToOffsetMapping mapping) {
    if (virtualFile.isEmpty()) {
      return;
    }
    timing.begin("Compute object offset mapping");
    virtualFile.computeMapping(appView, lazyDexStrings.size(), timing, mapping);
    timing.end();
    timing.begin("Rewrite jumbo strings");
    rewriteCodeWithJumboStrings(virtualFile.getObjectMapping(), virtualFile.classes());
    timing.end();
  }
}
