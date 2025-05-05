// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.reflectiveidentification;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.shaking.Enqueuer;

public class EnqueuerReflectiveIdentification extends ReflectiveIdentification {

  private final Enqueuer enqueuer;

  public EnqueuerReflectiveIdentification(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    super(appView, new EnqueuerReflectiveIdentificationEventConsumer(appView, enqueuer));
    this.enqueuer = enqueuer;
  }

  @Override
  protected boolean processInvoke(ProgramMethod method, InvokeMethod invoke) {
    if (super.processInvoke(method, invoke)) {
      return true;
    }
    return enqueuer.getAnalyses().handleReflectiveInvoke(method, invoke);
  }
}
