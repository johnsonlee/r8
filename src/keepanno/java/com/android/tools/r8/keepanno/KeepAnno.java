// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions.getR8Options;

import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import java.util.function.Consumer;
import org.objectweb.asm.ClassVisitor;

/** Keep annotation APIs */
public class KeepAnno {
  /**
   * Experimental API to extract keep rules from keep annotations.
   *
   * <p>Create a {@code ClassVisitor} to extract keep rules from keep annotations present in the
   * code visited.
   *
   * <p>Each created {@code ClassVisitor} is independent and share no state (except for the {@code
   * consumer} if the same is used for creating multiple {@code ClassVisitors}) and they can be used
   * concurrently visiting different classes. If the same {@code consumer} is used for creating
   * multiple {@code ClassVisitors}, it must be thread safe.
   */
  public static ClassVisitor createClassVisitorForKeepRulesExtraction(Consumer<String> consumer) {
    KeepRuleExtractor extractor = new KeepRuleExtractor(consumer, getR8Options());
    return KeepEdgeReader.getClassVisitor(extractor::extract);
  }
}
