// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexProgramClass;
import java.util.Collection;

public class InternalProgramClassProvider implements ProgramResourceProvider {

  private final Collection<DexProgramClass> classes;

  public InternalProgramClassProvider(Collection<DexProgramClass> classes) {
    this.classes = classes;
  }

  public Collection<DexProgramClass> getClasses() {
    return classes;
  }

  @Override
  public Collection<ProgramResource> getProgramResources() throws ResourceException {
    throw new Unreachable();
  }
}
