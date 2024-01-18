// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.TestBase.toDexType;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.verticalclassmerging.VerticallyMergedClasses;
import java.util.HashSet;
import java.util.Set;

public class VerticallyMergedClassesInspector {

  private final DexItemFactory dexItemFactory;
  private final VerticallyMergedClasses verticallyMergedClasses;

  private final Set<ClassReference> seen = new HashSet<>();

  public VerticallyMergedClassesInspector(
      DexItemFactory dexItemFactory, VerticallyMergedClasses verticallyMergedClasses) {
    this.dexItemFactory = dexItemFactory;
    this.verticallyMergedClasses = verticallyMergedClasses;
  }

  public VerticallyMergedClassesInspector assertMergedIntoSubtype(Class<?> clazz) {
    return assertMergedIntoSubtype(Reference.classFromClass(clazz));
  }

  public VerticallyMergedClassesInspector assertMergedIntoSubtype(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      assertMergedIntoSubtype(clazz);
    }
    return this;
  }

  public VerticallyMergedClassesInspector assertMergedIntoSubtype(ClassReference classReference) {
    assertTrue(
        verticallyMergedClasses.hasBeenMergedIntoSubtype(
            toDexType(classReference, dexItemFactory)));
    seen.add(classReference);
    return this;
  }

  public VerticallyMergedClassesInspector assertMergedIntoSubtype(String... typeNames) {
    for (String typeName : typeNames) {
      assertMergedIntoSubtype(Reference.classFromTypeName(typeName));
    }
    return this;
  }

  public VerticallyMergedClassesInspector assertNoClassesMerged() {
    assertTrue(verticallyMergedClasses.isEmpty());
    return this;
  }

  public VerticallyMergedClassesInspector assertNoOtherClassesMerged() {
    for (DexType source : verticallyMergedClasses.getSources()) {
      assertTrue(source.getTypeName(), seen.contains(source.asClassReference()));
    }
    return this;
  }
}
