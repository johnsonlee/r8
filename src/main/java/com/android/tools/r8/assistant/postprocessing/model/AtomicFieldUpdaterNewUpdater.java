// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public class AtomicFieldUpdaterNewUpdater extends ReflectiveEvent {
  private final DexField field;

  protected AtomicFieldUpdaterNewUpdater(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    assert args.length == 3;
    DexType type = toType(args[0], factory);
    DexType holder = toType(args[1], factory);
    String fieldName = args[2];
    field = factory.createField(holder, type, fieldName);
  }

  public DexField getField() {
    return field;
  }

  @Override
  public boolean isAtomicFieldUpdaterNewUpdater() {
    return true;
  }

  @Override
  public AtomicFieldUpdaterNewUpdater asAtomicFieldUpdaterNewUpdater() {
    return this;
  }

  @Override
  public String getContentsString() {
    return field.toString();
  }
}
