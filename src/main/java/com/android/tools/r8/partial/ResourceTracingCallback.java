// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.ResourceShrinker.ReferenceChecker;
import com.android.tools.r8.utils.DescriptorUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class ResourceTracingCallback implements ReferenceChecker {

  private final IntSet potentialIds = new IntOpenHashSet();

  public IntSet getPotentialIds() {
    return potentialIds;
  }

  @Override
  public boolean shouldProcess(String internalName) {
    // Only process R classes.
    return DescriptorUtils.isRClassDescriptor(
        DescriptorUtils.internalNameToDescriptor(internalName));
  }

  @Override
  public void referencedInt(int value) {
    potentialIds.add(value);
  }

  @Override
  public void referencedString(String value) {}

  @Override
  public void referencedStaticField(String internalName, String fieldName) {}

  @Override
  public void referencedMethod(String internalName, String methodName, String methodDescriptor) {}
}
