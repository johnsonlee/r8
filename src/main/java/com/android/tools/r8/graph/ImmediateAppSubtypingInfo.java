// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ImmediateAppSubtypingInfo extends ImmediateSubtypingInfo<DexClass, DexClass> {

  private ImmediateAppSubtypingInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<DexClass, List<DexClass>> immediateSubtypes) {
    super(appView, immediateSubtypes);
  }

  public static ImmediateAppSubtypingInfo create(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    DirectMappedDexApplication app = appView.app().asDirect();
    Iterable<DexClass> classes =
        Iterables.concat(app.programClasses(), app.classpathClasses(), app.libraryClasses());
    return internalCreate(
        appView,
        classes,
        Function.identity(),
        immediateSubtypes -> new ImmediateAppSubtypingInfo(appView, immediateSubtypes));
  }
}
