// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.ClassFlag;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;

public class ClassFlagEvent extends ReflectiveEvent {
  private final DexType holder;
  private final ClassFlag classFlag;

  protected ClassFlagEvent(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    assert args.length == 2;
    holder = toType(args[0], factory);
    classFlag = ClassFlag.valueOf(args[1]);
  }

  public ClassFlag getClassFlag() {
    return classFlag;
  }

  @Override
  public boolean isClassFlagEvent() {
    return true;
  }

  @Override
  public ClassFlagEvent asClassFlagEvent() {
    return this;
  }

  @Override
  public String getContentsString() {
    return holder.toString() + "#" + classFlag;
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    // TODO(b/428836085): Check inner properties of the keep rules, holder, type and name may have
    //  to be preserved.
    return keepInfoCollectionExported.getKeepClassInfo(holder.asTypeReference()) != null;
  }
}
