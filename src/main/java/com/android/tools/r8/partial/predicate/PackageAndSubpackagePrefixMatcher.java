// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.predicate;

import static com.android.tools.r8.partial.predicate.R8PartialPredicateUtils.descriptorPrefixPatternToJavaTypePattern;

import com.android.tools.r8.graph.DexString;
import java.io.UTFDataFormatException;

public class PackageAndSubpackagePrefixMatcher implements R8PartialPredicate {

  private final byte[] descriptorPrefix;
  private final int descriptorPrefixLength;

  public PackageAndSubpackagePrefixMatcher(String descriptorPrefix) {
    assert descriptorPrefix.charAt(0) == 'L';
    assert descriptorPrefix.charAt(descriptorPrefix.length() - 1) == '/';
    this.descriptorPrefix = DexString.encodeToMutf8(descriptorPrefix);
    this.descriptorPrefixLength = descriptorPrefix.length();
  }

  @Override
  public boolean test(DexString descriptor) {
    return descriptor.startsWith(descriptorPrefix);
  }

  @Override
  public String serializeToString() {
    try {
      return descriptorPrefixPatternToJavaTypePattern(
          DexString.decodeFromMutf8(descriptorPrefix, descriptorPrefixLength) + "**");
    } catch (UTFDataFormatException e) {
      throw new RuntimeException(e);
    }
  }
}
