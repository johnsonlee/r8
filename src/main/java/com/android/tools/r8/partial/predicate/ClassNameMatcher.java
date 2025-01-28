// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.predicate;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.DescriptorUtils;

public class ClassNameMatcher implements R8PartialPredicate {

  private final String descriptor;

  public ClassNameMatcher(String descriptor) {
    assert descriptor.charAt(0) == 'L';
    assert descriptor.charAt(descriptor.length() - 1) == ';';
    this.descriptor = descriptor;
  }

  @Override
  public boolean test(DexString descriptor) {
    return descriptor.toString().equals(this.descriptor);
  }

  @Override
  public String serializeToString() {
    return DescriptorUtils.descriptorToJavaType(descriptor);
  }
}
