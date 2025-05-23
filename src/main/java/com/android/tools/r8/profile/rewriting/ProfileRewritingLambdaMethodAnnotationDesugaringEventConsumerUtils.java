// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.profile.art.ArtProfileOptions;

public class ProfileRewritingLambdaMethodAnnotationDesugaringEventConsumerUtils {

  static void handleLambdaMethodAnnotationDesugaringClassContext(
      DexProgramClass a,
      ProgramDefinition context,
      ConcreteProfileCollectionAdditions additionsCollection,
      ArtProfileOptions options) {
    if (options.isIncludingLambdaMethodAnnotation()) {
      additionsCollection.applyIfContextIsInProfile(
          context,
          additionsBuilder -> {
            additionsBuilder.addRule(a);
            a.forEachProgramMethod(additionsBuilder::addRule);
          });
    }
  }
}
