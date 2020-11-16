// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Policy that enforces that methods are only merged if they have the same visibility and library
 * method override information.
 */
public class PreserveMethodCharacteristics extends MultiClassPolicy {

  static class MethodCharacteristics {

    private final int visibilityOrdinal;

    private MethodCharacteristics(DexEncodedMethod method) {
      this.visibilityOrdinal = method.getAccessFlags().getVisibilityOrdinal();
    }

    @Override
    public int hashCode() {
      return visibilityOrdinal;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      MethodCharacteristics characteristics = (MethodCharacteristics) obj;
      return visibilityOrdinal == characteristics.visibilityOrdinal;
    }
  }

  public PreserveMethodCharacteristics() {}

  public static class TargetGroup {

    private final List<DexProgramClass> group = new LinkedList<>();
    private final Map<DexMethodSignature, MethodCharacteristics> methodMap = new HashMap<>();

    public List<DexProgramClass> getGroup() {
      return group;
    }

    public boolean tryAdd(DexProgramClass clazz) {
      Map<DexMethodSignature, MethodCharacteristics> newMethods = new HashMap<>();
      for (DexEncodedMethod method : clazz.methods()) {
        DexMethodSignature signature = method.getSignature();
        MethodCharacteristics existingCharacteristics = methodMap.get(signature);
        MethodCharacteristics methodCharacteristics = new MethodCharacteristics(method);
        if (existingCharacteristics == null) {
          newMethods.put(signature, methodCharacteristics);
          continue;
        }
        if (!methodCharacteristics.equals(existingCharacteristics)) {
          return false;
        }
      }
      methodMap.putAll(newMethods);
      group.add(clazz);
      return true;
    }
  }

  @Override
  public Collection<? extends List<DexProgramClass>> apply(List<DexProgramClass> group) {
    List<TargetGroup> groups = new ArrayList<>();

    for (DexProgramClass clazz : group) {
      boolean added = Iterables.any(groups, targetGroup -> targetGroup.tryAdd(clazz));
      if (!added) {
        TargetGroup newGroup = new TargetGroup();
        added = newGroup.tryAdd(clazz);
        assert added;
        groups.add(newGroup);
      }
    }

    Collection<List<DexProgramClass>> newGroups = new ArrayList<>();
    for (TargetGroup newGroup : groups) {
      if (!isTrivial(newGroup.getGroup())) {
        newGroups.add(newGroup.getGroup());
      }
    }
    return newGroups;
  }
}