// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.covariantreturntype;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;

public class CovariantReturnTypeReferences {

  static String COVARIANT_RETURN_TYPE_DESCRIPTOR =
      "Ldalvik/annotation/codegen/CovariantReturnType;";

  final DexType annotationCovariantReturnType;
  final DexType annotationCovariantReturnTypes;

  final DexString presentAfterName;
  final DexString returnTypeName;
  final DexString valueName;

  CovariantReturnTypeReferences(DexItemFactory factory) {
    this.annotationCovariantReturnType = factory.createType(COVARIANT_RETURN_TYPE_DESCRIPTOR);
    this.annotationCovariantReturnTypes =
        factory.createType("Ldalvik/annotation/codegen/CovariantReturnType$CovariantReturnTypes;");
    this.presentAfterName = factory.createString("presentAfter");
    this.returnTypeName = factory.createString("returnType");
    this.valueName = factory.createString("value");
  }

  boolean isCovariantReturnTypeAnnotation(DexType type) {
    return type.isIdenticalTo(annotationCovariantReturnType);
  }

  boolean isCovariantReturnTypesAnnotation(DexType type) {
    return type.isIdenticalTo(annotationCovariantReturnTypes);
  }

  boolean isOneOfCovariantReturnTypeAnnotations(DexType type) {
    return isCovariantReturnTypeAnnotation(type) || isCovariantReturnTypesAnnotation(type);
  }
}
