// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.D8Command;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.utils.InternalClasspathOrLibraryClassProvider;
import com.android.tools.r8.utils.InternalProgramClassProvider;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.SetUtils;
import java.io.IOException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class R8PartialD8Input {

  private final Collection<DexProgramClass> d8Classes;
  private final Collection<DexProgramClass> r8Classes;
  private final Map<DexType, DexClasspathClass> classpathClasses;
  private final Map<DexType, DexLibraryClass> libraryClasses;
  private final List<KeepDeclaration> keepDeclarations;

  public R8PartialD8Input(
      Collection<DexProgramClass> d8Classes,
      Collection<DexProgramClass> r8Classes,
      Collection<DexClasspathClass> classpathClasses,
      Collection<DexLibraryClass> libraryClasses,
      List<KeepDeclaration> keepDeclarations) {
    this.d8Classes = d8Classes;
    this.r8Classes = r8Classes;
    this.classpathClasses =
        MapUtils.transform(classpathClasses, IdentityHashMap::new, DexClass::getType);
    this.libraryClasses =
        MapUtils.transform(libraryClasses, IdentityHashMap::new, DexClass::getType);
    this.keepDeclarations = keepDeclarations;
  }

  public void configure(D8Command.Builder commandBuilder) throws IOException {
    commandBuilder
        .addProgramResourceProvider(new InternalProgramClassProvider(d8Classes))
        .addProgramResourceProvider(new InternalProgramClassProvider(r8Classes))
        .addClasspathResourceProvider(
            new InternalClasspathOrLibraryClassProvider<>(classpathClasses))
        .addLibraryResourceProvider(new InternalClasspathOrLibraryClassProvider<>(libraryClasses));
  }

  public Set<DexType> getD8Types() {
    // Intentionally not returning d8Classes.keySet(). This allows clearing the map after providing
    // the classes to the D8 compilation.
    return SetUtils.mapIdentityHashSet(d8Classes, DexClass::getType);
  }

  public Set<DexType> getR8Types() {
    // Intentionally not returning r8Classes.keySet(). This allows clearing the map after providing
    // the classes to the D8 compilation.
    return SetUtils.mapIdentityHashSet(r8Classes, DexClass::getType);
  }

  public List<KeepDeclaration> getKeepDeclarations() {
    return keepDeclarations;
  }
}
