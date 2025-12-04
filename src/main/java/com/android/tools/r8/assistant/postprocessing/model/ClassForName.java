// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;

public class ClassForName extends ReflectiveEvent {

  private final ClassReference className;
  private final boolean initialize;
  private final ClassReference classLoader;

  public ClassForName(ReflectiveEventType eventType, String[] stack, String[] args) {
    super(eventType, stack);
    assert args.length == 3;
    className = Reference.classFromTypeName(args[0]);
    initialize = Boolean.parseBoolean(args[1]);
    classLoader = Reference.classFromTypeName(args[2]);
  }

  @Override
  public String getContentsString() {
    return className.getTypeName()
        + ", initialize: "
        + initialize
        + ", classloader: "
        + classLoader.getTypeName();
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    // TODO(b/428836085): Check inner properties of the keep rules, holder, type and name may have
    //  to be preserved.
    return keepInfoCollectionExported.getKeepClassInfo(className) != null;
  }

  @Override
  public boolean isClassForName() {
    return true;
  }

  @Override
  public ClassForName asClassForName() {
    return this;
  }

  public ClassReference getClassName() {
    return className;
  }
}
