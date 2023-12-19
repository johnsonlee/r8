// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import java.util.Collection;
import java.util.Map;

public class NoNestedMergingPolicy
    extends VerticalClassMergerPolicyWithPreprocessing<Map<DexProgramClass, VerticalMergeGroup>> {

  @Override
  public boolean canMerge(
      VerticalMergeGroup group, Map<DexProgramClass, VerticalMergeGroup> groups) {
    if (groups.containsKey(group.getTarget())) {
      VerticalMergeGroup removedGroup = groups.remove(group.getSource());
      assert removedGroup == group;
      return false;
    }
    return true;
  }

  @Override
  public Map<DexProgramClass, VerticalMergeGroup> preprocess(
      Collection<VerticalMergeGroup> groups) {
    return MapUtils.newIdentityHashMap(
        builder -> {
          for (VerticalMergeGroup group : groups) {
            builder.put(group.getSource(), group);
          }
        });
  }

  @Override
  public String getName() {
    return "NoNestedMergingPolicy";
  }
}
