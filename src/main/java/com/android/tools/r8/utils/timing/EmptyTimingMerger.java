// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import java.util.Collection;

public class EmptyTimingMerger implements TimingMerger {

  private static final EmptyTimingMerger INSTANCE = new EmptyTimingMerger();

  static EmptyTimingMerger get() {
    return INSTANCE;
  }

  @Override
  public void add(Collection<Timing> timings) {}

  @Override
  public void end() {}

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public TimingMerger disableSlowestReporting() {
    return this;
  }
}
