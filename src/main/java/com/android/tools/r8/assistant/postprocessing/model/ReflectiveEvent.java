// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import java.util.Arrays;

public class ReflectiveEvent {

  private final ReflectiveEventType eventType;
  private final String[] stack;
  private final String[] args;

  protected ReflectiveEvent(ReflectiveEventType eventType, String[] stack, String[] args) {
    this.eventType = eventType;
    this.stack = stack;
    this.args = args;
  }

  @Override
  public String toString() {
    return eventType + (stack != null ? "[s]" : "") + "(" + Arrays.toString(args) + ")";
  }

  public static ReflectiveEvent instantiate(
      ReflectiveEventType eventType, String[] stack, String[] args) {
    // TODO(b/428836085): Switch on the eventType and build a subclass, make this class abstract.
    return new ReflectiveEvent(eventType, stack, args);
  }
}
