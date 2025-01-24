// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class InternalClasspathOrLibraryClassProvider<T extends DexClass>
    implements ClassFileResourceProvider {

  private final Map<DexType, T> classes;

  public InternalClasspathOrLibraryClassProvider(Map<DexType, T> classes) {
    this.classes = classes;
  }

  public T getClass(DexType type) {
    return classes.get(type);
  }

  public Collection<DexType> getTypes() {
    return classes.keySet();
  }

  @Override
  public Set<String> getClassDescriptors() {
    throw new Unreachable();
  }

  @Override
  public ProgramResource getProgramResource(String descriptor) {
    throw new Unreachable();
  }
}
