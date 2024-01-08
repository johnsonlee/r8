// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.KeepEdgeReader.Parent;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Constraints;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.ast.ParsingContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class KeepConstraintsVisitor extends AnnotationVisitorBase {

  private final Parent<Collection<KeepOption>> parent;
  private final Set<KeepOption> options = new HashSet<>();

  public KeepConstraintsVisitor(
      ParsingContext parsingContext, Parent<Collection<KeepOption>> parent) {
    super(parsingContext);
    this.parent = parent;
  }

  @Override
  public void visitEnum(String ignore, String descriptor, String value) {
    if (!descriptor.equals(AnnotationConstants.Constraints.DESCRIPTOR)) {
      super.visitEnum(ignore, descriptor, value);
    }
    switch (value) {
      case Constraints.LOOKUP:
        options.add(KeepOption.SHRINKING);
        break;
      case Constraints.NAME:
        options.add(KeepOption.OBFUSCATING);
        break;
      case Constraints.VISIBILITY_RELAX:
        // The compiler currently satisfies that access is never restricted.
        break;
      case Constraints.VISIBILITY_RESTRICT:
        // We don't have directional rules so this prohibits any modification.
        options.add(KeepOption.ACCESS_MODIFICATION);
        break;
      case Constraints.CLASS_INSTANTIATE:
      case Constraints.METHOD_INVOKE:
      case Constraints.FIELD_GET:
      case Constraints.FIELD_SET:
        // These options are the item-specific actual uses of the items.
        // Allocating, invoking and read/writing all imply that the item cannot be "optimized"
        // at compile time. It would be natural to refine the field specific uses but that is
        // not expressible as keep options.
        options.add(KeepOption.OPTIMIZING);
        break;
      case Constraints.METHOD_REPLACE:
      case Constraints.FIELD_REPLACE:
      case Constraints.NEVER_INLINE:
      case Constraints.CLASS_OPEN_HIERARCHY:
        options.add(KeepOption.OPTIMIZING);
        break;
      case Constraints.ANNOTATIONS:
        // The annotation constrain only implies that annotations should remain, no restrictions
        // are on the item otherwise.
        options.add(KeepOption.ANNOTATION_REMOVAL);
        break;
      default:
        super.visitEnum(ignore, descriptor, value);
    }
  }

  @Override
  public void visitEnd() {
    parent.accept(options);
    super.visitEnd();
  }
}
