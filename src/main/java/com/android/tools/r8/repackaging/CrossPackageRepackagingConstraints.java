// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.repackaging;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.MapUtils;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records cross-package repackaging constraints that a given class from one package can only be
 * repackaged if other classes from other packages have not been repackaged.
 */
public class CrossPackageRepackagingConstraints {

  private final Map<DexProgramClass, Set<DexProgramClass>> notRepackagedConstraints =
      new ConcurrentHashMap<>();

  public void onlyRepackageClassIfOtherClassIsNotRepackaged(
      DexProgramClass clazz, DexProgramClass otherClass) {
    notRepackagedConstraints
        .computeIfAbsent(clazz, ignoreKey(ConcurrentHashMap::newKeySet))
        .add(otherClass);
  }

  public Set<DexProgramClass> removeNotRepackagedConstraints(DexProgramClass clazz) {
    return MapUtils.removeOrDefault(notRepackagedConstraints, clazz, Collections.emptySet());
  }
}
