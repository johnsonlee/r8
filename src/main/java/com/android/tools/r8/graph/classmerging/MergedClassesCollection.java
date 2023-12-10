// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public class MergedClassesCollection implements MergedClasses {

  private List<MergedClasses> collection = new ArrayList<>();

  public void add(MergedClasses mergedClasses) {
    collection.add(mergedClasses);
  }

  @Override
  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    for (MergedClasses mergedClasses : collection) {
      mergedClasses.forEachMergeGroup(consumer);
    }
  }

  @Override
  public DexType getMergeTargetOrDefault(DexType type, DexType defaultValue) {
    throw new Unreachable();
  }

  @Override
  public boolean isMergeSource(DexType type) {
    for (MergedClasses mergedClasses : collection) {
      if (mergedClasses.isMergeSource(type)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isMergeTarget(DexType type) {
    for (MergedClasses mergedClasses : collection) {
      if (mergedClasses.isMergeTarget(type)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView) {
    for (MergedClasses mergedClasses : collection) {
      assert mergedClasses.verifyAllSourcesPruned(appView);
    }
    return true;
  }
}
