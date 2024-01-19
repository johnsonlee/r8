// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class NoClassInitializationChangesPolicy
    extends VerticalClassMergerPolicyWithPreprocessing<Map<DexProgramClass, Set<DexProgramClass>>> {

  private final AppView<AppInfoWithLiveness> appView;

  public NoClassInitializationChangesPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(
      VerticalMergeGroup group,
      Map<DexProgramClass, Set<DexProgramClass>> sourcesWithClassInitializers) {
    // For interface types, this is more complicated, see:
    // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-5.html#jvms-5.5
    // We basically can't move the clinit, since it is not called when implementing classes have
    // their clinit called - except when the interface has a default method.
    DexProgramClass sourceClass = group.getSource();
    DexProgramClass targetClass = group.getTarget();
    // TODO(b/320433836): Add support for concatenating <clinit>s.
    if (sourceClass.hasClassInitializer()) {
      if (targetClass.hasClassInitializer()
          || sourcesWithClassInitializers.get(targetClass).size() > 1) {
        boolean removed = sourcesWithClassInitializers.get(targetClass).remove(sourceClass);
        assert removed;
        return false;
      }
    }
    assert !sourceClass.hasClassInitializer() || !targetClass.hasClassInitializer();
    return !targetClass.classInitializationMayHaveSideEffects(
            appView, type -> type.isIdenticalTo(sourceClass.getType()))
        && (!sourceClass.isInterface()
            || !sourceClass.classInitializationMayHaveSideEffects(appView));
  }

  @Override
  public Map<DexProgramClass, Set<DexProgramClass>> preprocess(
      Collection<VerticalMergeGroup> groups) {
    Map<DexProgramClass, Set<DexProgramClass>> sourcesWithClassInitializers =
        new IdentityHashMap<>();
    for (VerticalMergeGroup group : groups) {
      if (group.getSource().hasClassInitializer()) {
        sourcesWithClassInitializers
            .computeIfAbsent(group.getTarget(), ignoreKey(Sets::newIdentityHashSet))
            .add(group.getSource());
      }
    }
    return sourcesWithClassInitializers;
  }

  @Override
  public String getName() {
    return "NoClassInitializationChangesPolicy";
  }
}
