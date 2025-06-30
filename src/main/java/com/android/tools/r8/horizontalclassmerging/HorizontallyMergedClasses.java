// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClasses;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.EmptyBidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import java.util.Set;
import java.util.function.BiConsumer;

public class HorizontallyMergedClasses implements MergedClasses {

  private final BidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses;

  public HorizontallyMergedClasses(
      BidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses) {
    this.mergedClasses = mergedClasses;
  }

  static Builder builder() {
    return new Builder();
  }

  public static HorizontallyMergedClasses empty() {
    return new HorizontallyMergedClasses(new EmptyBidirectionalOneToOneMap<>());
  }

  @Override
  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    mergedClasses.forEachManyToOneMapping(consumer);
  }

  @Override
  public DexType getMergeTargetOrDefault(DexType type, DexType defaultValue) {
    return mergedClasses.getOrDefault(type, defaultValue);
  }

  public Set<DexType> getSources() {
    return mergedClasses.keySet();
  }

  @Override
  public Set<DexType> getSourcesFor(DexType type) {
    return mergedClasses.getKeys(type);
  }

  public Set<DexType> getTargets() {
    return mergedClasses.values();
  }

  public boolean isEmpty() {
    return mergedClasses.isEmpty();
  }

  @Override
  public boolean isMergeSource(DexType type) {
    return mergedClasses.containsKey(type);
  }

  @Override
  public boolean isMergeTarget(DexType type) {
    return mergedClasses.containsValue(type);
  }

  BidirectionalManyToOneRepresentativeMap<DexType, DexType> getBidirectionalMap() {
    return mergedClasses;
  }

  @Override
  public boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView) {
    for (DexType source : mergedClasses.keySet()) {
      assert appView.appInfo().wasPruned(source)
          : "Expected horizontally merged lambda class `"
              + source.toSourceString()
              + "` to be absent";
    }
    return true;
  }

  public static class Builder {

    private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();

    void add(DexType source, DexType target) {
      assert !mergedClasses.containsKey(source);
      mergedClasses.put(source, target);
    }

    void addMergeGroup(HorizontalMergeGroup group) {
      group.forEachSource(clazz -> add(clazz.getType(), group.getTarget().getType()));
    }

    Builder addMergeGroups(Iterable<HorizontalMergeGroup> groups) {
      groups.forEach(this::addMergeGroup);
      return this;
    }

    HorizontallyMergedClasses build() {
      return new HorizontallyMergedClasses(mergedClasses);
    }
  }
}
