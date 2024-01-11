// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.ParsingContext;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

public abstract class AnnotationVisitorBase extends AnnotationVisitor {

  private final ParsingContext parsingContext;

  AnnotationVisitorBase(ParsingContext parsingContext) {
    super(KeepEdgeReader.ASM_VERSION);
    this.parsingContext = parsingContext;
  }

  private String getTypeName(String descriptor) {
    return Type.getType(descriptor).getClassName();
  }

  public ParsingContext getParsingContext() {
    return parsingContext;
  }

  @Override
  public void visit(String name, Object value) {
    throw parsingContext.error("Unexpected value for property " + name + " with value " + value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String descriptor) {
    throw parsingContext.error(
        "Unexpected annotation for property "
            + name
            + " of annotation type "
            + getTypeName(descriptor));
  }

  @Override
  public void visitEnum(String name, String descriptor, String value) {
    throw parsingContext.error(
        "Unexpected enum for property "
            + name
            + " of enum type "
            + getTypeName(descriptor)
            + " with value "
            + value);
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    throw parsingContext.error("Unexpected array for property " + name);
  }
}
