// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MergeGroupBase;

public class VerticalMergeGroup extends MergeGroupBase {

  private final DexProgramClass source;
  private final DexProgramClass target;

  VerticalMergeGroup(DexProgramClass source, DexProgramClass target) {
    this.source = source;
    this.target = target;
  }

  public DexProgramClass getSource() {
    return source;
  }

  public DexProgramClass getTarget() {
    return target;
  }

  @Override
  public int size() {
    return 2;
  }
}
