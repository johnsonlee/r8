// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public class ClassGetMembers extends ReflectiveEvent {

  private final DexType holder;

  protected ClassGetMembers(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    holder = toType(args[0], factory);
  }

  public DexType getHolder() {
    return holder;
  }

  @Override
  public boolean isClassGetMembers() {
    return true;
  }

  @Override
  public ClassGetMembers asClassGetMembers() {
    return this;
  }

  @Override
  public String getContentsString() {
    return holder.toSourceString();
  }
}
