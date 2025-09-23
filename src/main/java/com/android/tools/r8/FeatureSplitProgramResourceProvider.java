// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;

/**
 * A wrapper around the ProgramResourceProvider of a feature split, which intentionally returns an
 * empty DataResourceProvider.
 */
public class FeatureSplitProgramResourceProvider implements ProgramResourceProvider {

  private final ProgramResourceProvider programResourceProvider;

  private DexItemFactory factory;
  private Set<DexType> types = Sets.newIdentityHashSet();

  public FeatureSplitProgramResourceProvider(ProgramResourceProvider programResourceProvider) {
    this.programResourceProvider = programResourceProvider;
  }

  @Override
  public Collection<ProgramResource> getProgramResources() throws ResourceException {
    assert factory != null;
    // If the types in this provider has been unset, then the ClassToFeatureSplitMap has already
    // been created and we no longer need tracking.
    if (types == null) {
      return programResourceProvider.getProgramResources();
    }
    Collection<ProgramResource> programResources = programResourceProvider.getProgramResources();
    for (ProgramResource programResource : programResources) {
      for (String classDescriptor : programResource.getClassDescriptors()) {
        types.add(factory.createType(classDescriptor));
      }
    }
    return programResources;
  }

  @Override
  public DataResourceProvider getDataResourceProvider() {
    return null;
  }

  public void setDexItemFactory(DexItemFactory factory) {
    this.factory = factory;
  }

  public Set<DexType> unsetTypes() {
    Set<DexType> result = types;
    types = null;
    return result;
  }
}
