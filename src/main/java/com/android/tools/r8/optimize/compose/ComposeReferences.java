// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.lens.GraphLens;

public class ComposeReferences {

  public final DexString skipToGroupEndName;
  public final DexType composableType;
  public final DexMethod updatedChangedFlagsMethod;

  public ComposeReferences(DexItemFactory factory) {
    skipToGroupEndName = factory.createString("skipToGroupEnd");
    composableType = factory.createType("Landroidx/compose/runtime/Composable;");
    updatedChangedFlagsMethod =
        factory.createMethod(
            factory.createType("Landroidx/compose/runtime/RecomposeScopeImplKt;"),
            factory.createProto(factory.intType, factory.intType),
            "updateChangedFlags");
  }

  public ComposeReferences(
      DexString skipToGroupEndName, DexType composableType, DexMethod updatedChangedFlagsMethod) {
    this.skipToGroupEndName = skipToGroupEndName;
    this.composableType = composableType;
    this.updatedChangedFlagsMethod = updatedChangedFlagsMethod;
  }

  public boolean isComposable(ProgramDefinition definition) {
    return definition.isProgramMethod()
        && definition.asProgramMethod().getAnnotations().hasAnnotation(composableType);
  }

  public ComposeReferences rewrittenWithLens(GraphLens graphLens, GraphLens codeLens) {
    return new ComposeReferences(
        skipToGroupEndName,
        graphLens.lookupClassType(composableType, codeLens),
        graphLens.getRenamedMethodSignature(updatedChangedFlagsMethod, codeLens));
  }
}
