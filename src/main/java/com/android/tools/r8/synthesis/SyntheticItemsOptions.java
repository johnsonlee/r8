// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.SystemPropertyUtils;
import java.util.Collection;
import java.util.function.Predicate;

public class SyntheticItemsOptions {

  public boolean restrictRenaming =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.synthesis.restrictrenaming", false);

  public boolean isMinificationAllowed(
      DexProgramClass clazz, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return internalIsPropertyAllowed(
        clazz,
        appView,
        syntheticContext ->
            appView.getKeepInfo(syntheticContext).isMinificationAllowed(appView.options()));
  }

  public boolean isRepackagingAllowed(
      DexProgramClass clazz, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return internalIsPropertyAllowed(
        clazz,
        appView,
        syntheticContext ->
            appView.getKeepInfo(syntheticContext).isRepackagingAllowed(appView.options()));
  }

  private boolean internalIsPropertyAllowed(
      DexProgramClass clazz,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Predicate<DexProgramClass> isPropertyAllowed) {
    if (restrictRenaming) {
      SyntheticItems syntheticItems = appView.getSyntheticItems();
      if (syntheticItems.isSynthetic(clazz)) {
        Collection<DexType> syntheticContextTypes =
            syntheticItems.getSynthesizingContextTypes(clazz.getType());
        for (DexType syntheticContextType : syntheticContextTypes) {
          DexProgramClass syntheticContext =
              asProgramClassOrNull(appView.definitionFor(syntheticContextType));
          if (syntheticContext != null && !isPropertyAllowed.test(syntheticContext)) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
