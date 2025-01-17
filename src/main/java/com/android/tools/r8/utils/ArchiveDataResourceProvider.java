// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.shaking.FilteredClassPath;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

public class ArchiveDataResourceProvider extends ArchiveResourceProvider {

  public ArchiveDataResourceProvider(Path archive) {
    super(FilteredClassPath.unfiltered(archive), false);
  }

  @Override
  public Collection<ProgramResource> getProgramResources() throws ResourceException {
    return Collections.emptyList();
  }
}
