// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.KeepEdgeReader.Parent;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Option;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class KeepOptionsVisitor extends AnnotationVisitorBase {

  private final Parent<Collection<KeepOption>> parent;
  private final Set<KeepOption> options = new HashSet<>();

  public KeepOptionsVisitor(ParsingContext parsingContext, Parent<Collection<KeepOption>> parent) {
    super(parsingContext);
    this.parent = parent;
  }

  @Override
  public void visitEnum(String ignore, String descriptor, String value) {
    if (!descriptor.equals(AnnotationConstants.Option.DESCRIPTOR)) {
      super.visitEnum(ignore, descriptor, value);
    }
    KeepOption option;
    switch (value) {
      case Option.SHRINKING:
        option = KeepOption.SHRINKING;
        break;
      case Option.OPTIMIZATION:
        option = KeepOption.OPTIMIZING;
        break;
      case Option.OBFUSCATION:
        option = KeepOption.OBFUSCATING;
        break;
      case Option.ACCESS_MODIFICATION:
        option = KeepOption.ACCESS_MODIFICATION;
        break;
      case Option.ANNOTATION_REMOVAL:
        option = KeepOption.ANNOTATION_REMOVAL;
        break;
      default:
        super.visitEnum(ignore, descriptor, value);
        return;
    }
    options.add(option);
  }

  @Override
  public void visitEnd() {
    parent.accept(options);
    super.visitEnd();
  }
}
