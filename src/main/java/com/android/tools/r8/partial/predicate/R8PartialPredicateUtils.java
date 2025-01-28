// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.predicate;

import static com.android.tools.r8.utils.DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.JAVA_PACKAGE_SEPARATOR;

public class R8PartialPredicateUtils {

  static String descriptorPrefixPatternToJavaTypePattern(String descriptorPrefixPattern) {
    assert descriptorPrefixPattern.charAt(0) == 'L';
    assert descriptorPrefixPattern.charAt(descriptorPrefixPattern.length() - 1) != ';';
    return descriptorPrefixPattern
        .substring(1)
        .replace(DESCRIPTOR_PACKAGE_SEPARATOR, JAVA_PACKAGE_SEPARATOR);
  }
}
