// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.List;
import java.util.Map;

public class ImmediateProgramSubtypingInfo
    extends ImmediateSubtypingInfo<DexProgramClass, DexProgramClass> {

  private ImmediateProgramSubtypingInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<DexProgramClass, List<DexProgramClass>> immediateSubtypes) {
    super(appView, immediateSubtypes);
  }

  public static ImmediateProgramSubtypingInfo create(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return internalCreate(
        appView,
        appView.appInfo().classes(),
        DexProgramClass::asProgramClassOrNull,
        immediateSubtypes -> new ImmediateProgramSubtypingInfo(appView, immediateSubtypes));
  }

  public static ImmediateProgramSubtypingInfo createWithDeterministicOrder(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return internalCreate(
        appView,
        appView.appInfo().classesWithDeterministicOrder(),
        DexProgramClass::asProgramClassOrNull,
        immediateSubtypes -> new ImmediateProgramSubtypingInfo(appView, immediateSubtypes));
  }

  @Override
  DexProgramClass toS(DexClass clazz) {
    return DexProgramClass.asProgramClassOrNull(clazz);
  }
}
