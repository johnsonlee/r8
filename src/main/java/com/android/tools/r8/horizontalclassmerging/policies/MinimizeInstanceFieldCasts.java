// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MinimizeInstanceFieldCasts extends MultiClassPolicy {

  @Override
  public final Collection<HorizontalMergeGroup> apply(HorizontalMergeGroup group) {
    // First find all classes that can be merged without changing field types.
    Map<Multiset<DexType>, HorizontalMergeGroup> newGroups = new LinkedHashMap<>();
    group.forEach(clazz -> addExact(clazz, newGroups));

    // Create a single group from all trivial groups.
    HorizontalMergeGroup pendingGroup = new HorizontalMergeGroup();
    newGroups
        .values()
        .removeIf(
            newGroup -> {
              if (newGroup.isTrivial()) {
                pendingGroup.add(newGroup);
                return true;
              }
              return false;
            });

    if (pendingGroup.isEmpty()) {
      return newGroups.values();
    }

    if (!pendingGroup.isTrivial()) {
      List<HorizontalMergeGroup> newGroupsIncludingRelaxedGroup =
          new ArrayList<>(newGroups.values());
      newGroupsIncludingRelaxedGroup.add(pendingGroup);
      return newGroupsIncludingRelaxedGroup;
    }

    HorizontalMergeGroup smallestNewGroup = null;
    for (HorizontalMergeGroup newGroup : newGroups.values()) {
      if (smallestNewGroup == null || newGroup.size() < smallestNewGroup.size()) {
        smallestNewGroup = newGroup;
      }
    }
    assert smallestNewGroup != null;
    smallestNewGroup.add(pendingGroup);
    return newGroups.values();
  }

  private void addExact(
      DexProgramClass clazz, Map<Multiset<DexType>, HorizontalMergeGroup> groups) {
    groups
        .computeIfAbsent(getExactMergeKey(clazz), ignore -> new HorizontalMergeGroup())
        .add(clazz);
  }

  private Multiset<DexType> getExactMergeKey(DexProgramClass clazz) {
    Multiset<DexType> fieldTypes = HashMultiset.create();
    for (DexEncodedField field : clazz.instanceFields()) {
      fieldTypes.add(field.getType());
    }
    return fieldTypes;
  }

  @Override
  public String getName() {
    return "MinimizeFieldCasts";
  }
}
