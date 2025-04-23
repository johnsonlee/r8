// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;

public interface UseCollectorEventConsumer {

  void notifyPresentClass(DexClass clazz, DefinitionContext referencedFrom);

  void notifyMissingClass(DexType type, DefinitionContext referencedFrom);

  void notifyPresentField(DexClassAndField field, DefinitionContext referencedFrom);

  void notifyMissingField(DexField field, DefinitionContext referencedFrom);

  void notifyPresentMethod(DexClassAndMethod method, DefinitionContext referencedFrom);

  void notifyPresentMethod(
      DexClassAndMethod method, DefinitionContext referencedFrom, DexMethod reference);

  void notifyMissingMethod(DexMethod method, DefinitionContext referencedFrom);

  void notifyPackageOf(Definition definition);
}
