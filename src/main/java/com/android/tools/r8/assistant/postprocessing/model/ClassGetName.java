// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.NameLookupType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;

public class ClassGetName extends ReflectiveEvent {

  private final DexType type;
  private final NameLookupType nameLookupType;

  protected ClassGetName(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    assert args.length == 2;
    type = toType(args[0], factory);
    nameLookupType = NameLookupType.valueOf(args[1]);
  }

  @Override
  public boolean isClassGetName() {
    return true;
  }

  @Override
  public ClassGetName asClassGetName() {
    return this;
  }

  public DexType getType() {
    return type;
  }

  public NameLookupType getNameLookupType() {
    return nameLookupType;
  }

  @Override
  public String getContentsString() {
    return type + ", " + nameLookupType;
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    // TODO(b/428836085): Check inner properties of the keep rules, holder, type and name may have
    //  to be preserved.
    return keepInfoCollectionExported.getKeepClassInfo(type.asTypeReference()) != null;
  }
}
