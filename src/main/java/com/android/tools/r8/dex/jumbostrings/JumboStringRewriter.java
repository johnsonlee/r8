// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.jumbostrings;

import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.ApplicationWriter.LazyDexString;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexWritableCode;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.android.tools.r8.utils.timing.TimingMerger;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class JumboStringRewriter {

  protected final AppView<?> appView;
  protected final InternalOptions options;
  protected final List<VirtualFile> virtualFiles;
  protected final List<LazyDexString> lazyDexStrings;

  public JumboStringRewriter(
      AppView<?> appView, List<LazyDexString> lazyDexStrings, List<VirtualFile> virtualFiles) {
    this.appView = appView;
    this.options = appView.options();
    this.lazyDexStrings = lazyDexStrings;
    this.virtualFiles = virtualFiles;
  }

  public static JumboStringRewriter create(
      AppView<?> appView, List<LazyDexString> lazyDexStrings, List<VirtualFile> virtualFiles) {
    return appView.options().enableContainerDex()
        ? new ContainerJumboStringRewriter(appView, lazyDexStrings, virtualFiles)
        : new JumboStringRewriter(appView, lazyDexStrings, virtualFiles);
  }

  public final void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    // Compute offsets and rewrite jumbo strings so that code offsets are fixed.
    TimingMerger merger = timing.beginMerger("Pre-write phase", executorService);
    Collection<Timing> timings = processVirtualFiles(executorService);
    merger.add(timings);
    merger.end();
  }

  protected Collection<Timing> processVirtualFiles(ExecutorService executorService)
      throws ExecutionException {
    return ThreadUtils.processItemsWithResults(
        virtualFiles, this::processVirtualFile, options.getThreadingModule(), executorService);
  }

  protected final Timing processVirtualFile(VirtualFile virtualFile) {
    Timing fileTiming = Timing.create("VirtualFile " + virtualFile.getId(), options);
    computeOffsetMappingAndRewriteJumboStrings(virtualFile, fileTiming);
    DebugRepresentation.computeForFile(appView, virtualFile);
    fileTiming.end();
    return fileTiming;
  }

  private void computeOffsetMappingAndRewriteJumboStrings(VirtualFile virtualFile, Timing timing) {
    if (virtualFile.isEmpty()) {
      return;
    }
    timing.begin("Compute object offset mapping");
    virtualFile.computeMapping(appView, lazyDexStrings.size(), timing);
    timing.end();
    timing.begin("Rewrite jumbo strings");
    rewriteCodeWithJumboStrings(virtualFile.getObjectMapping(), virtualFile.classes());
    timing.end();
  }

  /**
   * Rewrites the code for all methods in the given file so that they use JumboString for at least
   * the strings that require it in mapping.
   *
   * <p>If run multiple times on a class, the lowest index that is required to be a JumboString will
   * be used.
   */
  protected final void rewriteCodeWithJumboStrings(
      ObjectToOffsetMapping mapping, Collection<DexProgramClass> classes) {
    // Do not bail out early if forcing jumbo string processing.
    if (!options.getTestingOptions().forceJumboStringProcessing) {
      // If there are no strings with jumbo indices at all this is a no-op.
      if (!mapping.hasJumboStrings()) {
        return;
      }
    }
    for (DexProgramClass clazz : classes) {
      clazz.forEachProgramMethodMatching(
          DexEncodedMethod::hasCode,
          method -> {
            DexWritableCode code = method.getDefinition().getCode().asDexWritableCode();
            DexWritableCode rewrittenCode =
                code.rewriteCodeWithJumboStrings(
                    method,
                    mapping,
                    appView,
                    options.getTestingOptions().forceJumboStringProcessing);
            method.setCode(rewrittenCode.asCode(), appView);
          });
    }
  }
}
