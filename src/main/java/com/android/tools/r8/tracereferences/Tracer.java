// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import java.util.function.Predicate;

public class Tracer {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DiagnosticsHandler diagnostics;
  private final Predicate<DexType> targetPredicate;

  public Tracer(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      DiagnosticsHandler diagnostics,
      Predicate<DexType> targetPredicate) {
    this.appView = appView;
    this.diagnostics = diagnostics;
    this.targetPredicate = targetPredicate;
  }

  public void run(TraceReferencesConsumer consumer) {
    UseCollector useCollector = new UseCollector(appView, consumer, diagnostics, targetPredicate);
    useCollector.traceClasses(appView.appInfo().classes());
    consumer.finished(diagnostics);
  }
}
