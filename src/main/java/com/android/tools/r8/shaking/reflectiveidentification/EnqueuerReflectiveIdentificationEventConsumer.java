// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.reflectiveidentification;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer;

public class EnqueuerReflectiveIdentificationEventConsumer
    implements ReflectiveIdentificationEventConsumer {

  private final Enqueuer enqueuer;

  public EnqueuerReflectiveIdentificationEventConsumer(Enqueuer enqueuer) {
    this.enqueuer = enqueuer;
  }

  @Override
  public void onJavaLangClassNewInstance(DexProgramClass clazz, ProgramMethod context) {
    ProgramMethod defaultInitializer = clazz.getProgramDefaultInitializer();
    if (defaultInitializer != null) {
      enqueuer.traceReflectiveNewInstance(defaultInitializer, context);
    }
  }

  @Override
  public void onJavaLangReflectConstructorNewInstance(
      ProgramMethod initializer, ProgramMethod context) {
    enqueuer.traceReflectiveNewInstance(initializer, context);
  }
}
