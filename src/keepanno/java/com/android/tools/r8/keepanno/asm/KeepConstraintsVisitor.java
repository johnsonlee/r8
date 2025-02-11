// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.asm.KeepEdgeReader.Parent;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.Constraints;
import com.android.tools.r8.keepanno.ast.KeepConstraint;
import com.android.tools.r8.keepanno.ast.KeepConstraints;
import com.android.tools.r8.keepanno.ast.ParsingContext;

public class KeepConstraintsVisitor extends AnnotationVisitorBase {

  private final Parent<KeepConstraints> parent;
  private final KeepConstraints.Builder builder = KeepConstraints.builder();

  public KeepConstraintsVisitor(ParsingContext parsingContext, Parent<KeepConstraints> parent) {
    super(parsingContext);
    this.parent = parent;
  }

  @Override
  public void visitEnum(String ignore, String descriptor, String value) {
    if (!AnnotationConstants.Constraints.isDescriptor(descriptor)) {
      super.visitEnum(ignore, descriptor, value);
    }
    switch (value) {
      case Constraints.LOOKUP:
        builder.add(KeepConstraint.lookup());
        break;
      case Constraints.NAME:
        builder.add(KeepConstraint.name());
        break;
      case Constraints.VISIBILITY_RELAX:
        builder.add(KeepConstraint.visibilityRelax());
        break;
      case Constraints.VISIBILITY_RESTRICT:
        builder.add(KeepConstraint.visibilityRestrict());
        break;
      case Constraints.VISIBILITY_INVARIANT:
        builder.add(KeepConstraint.visibilityRelax());
        builder.add(KeepConstraint.visibilityRestrict());
        break;
      case Constraints.CLASS_INSTANTIATE:
        builder.add(KeepConstraint.classInstantiate());
        break;
      case Constraints.METHOD_INVOKE:
        builder.add(KeepConstraint.methodInvoke());
        break;
      case Constraints.FIELD_GET:
        builder.add(KeepConstraint.fieldGet());
        break;
      case Constraints.FIELD_SET:
        builder.add(KeepConstraint.fieldSet());
        break;
      case Constraints.METHOD_REPLACE:
        builder.add(KeepConstraint.methodReplace());
        break;
      case Constraints.FIELD_REPLACE:
        builder.add(KeepConstraint.fieldReplace());
        break;
      case Constraints.NEVER_INLINE:
        builder.add(KeepConstraint.neverInline());
        break;
      case Constraints.CLASS_OPEN_HIERARCHY:
        builder.add(KeepConstraint.classOpenHierarchy());
        break;
      case Constraints.GENERIC_SIGNATURE:
        builder.add(KeepConstraint.genericSignature());
        break;
      default:
        super.visitEnum(ignore, descriptor, value);
    }
  }

  @Override
  public void visitEnd() {
    parent.accept(builder.build());
    super.visitEnd();
  }
}
