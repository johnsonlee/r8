// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.records;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

public class RecordClassDesugaringPostProcessor extends RecordClassDesugaring
    implements CfPostProcessingDesugaring {

  private final DexType recordType;

  private RecordClassDesugaringPostProcessor(AppView<?> appView) {
    super(appView);
    this.recordType = appView.dexItemFactory().recordType;
  }

  public static RecordClassDesugaringPostProcessor create(AppView<?> appView) {
    return appView.options().desugarRecordState().isFull()
        ? new RecordClassDesugaringPostProcessor(appView)
        : null;
  }

  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> classes,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService,
      Timing timing) {
    try (Timing t0 = timing.begin("Record class desugaring")) {
      for (DexProgramClass clazz : classes) {
        if (clazz.isRecord()) {
          assert clazz.getSuperType().isIdenticalTo(recordType);
          clazz.getAccessFlags().unsetRecord();
        }
      }
    }
  }
}
