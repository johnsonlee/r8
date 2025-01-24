// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.utils.R8PartialCompilationConfiguration;
import com.android.tools.r8.utils.WorkList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Partitions the input classes to R8 partial into the classes for D8 and R8. */
public class R8PartialProgramPartioning {

  private final Set<DexProgramClass> d8Classes = new LinkedHashSet<>();
  private final Set<DexProgramClass> r8Classes = new LinkedHashSet<>();

  private R8PartialProgramPartioning() {}

  public static R8PartialProgramPartioning create(DirectMappedDexApplication app) {
    R8PartialProgramPartioning partioning = new R8PartialProgramPartioning();
    R8PartialCompilationConfiguration partialCompilationConfiguration =
        app.options.partialCompilationConfiguration;
    for (DexProgramClass clazz : app.classes()) {
      if (partialCompilationConfiguration.test(clazz)) {
        partioning.r8Classes.add(clazz);
      } else {
        partioning.d8Classes.add(clazz);
      }
    }
    // Collect all transitive superclasses of all D8 classes and treat these as D8 classes.
    WorkList<DexClass> worklist = WorkList.newIdentityWorkList(partioning.d8Classes);
    worklist.process(
        clazz ->
            clazz.forEachImmediateSuperClassMatching(
                app,
                (supertype, superclass) -> superclass != null && !superclass.isLibraryClass(),
                (supertype, superclass) -> {
                  if (superclass.isProgramClass()
                      && partioning.r8Classes.remove(superclass.asProgramClass())) {
                    partioning.d8Classes.add(superclass.asProgramClass());
                  }
                  worklist.addIfNotSeen(superclass);
                }));
    return partioning;
  }

  public Set<DexProgramClass> getD8Classes() {
    return d8Classes;
  }

  public Set<DexProgramClass> getR8Classes() {
    return r8Classes;
  }
}
