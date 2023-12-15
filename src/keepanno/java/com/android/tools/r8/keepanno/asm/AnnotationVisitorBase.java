// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;

public abstract class AnnotationVisitorBase extends AnnotationVisitor {

  AnnotationVisitorBase() {
    super(KeepEdgeReader.ASM_VERSION);
  }

  public abstract String getAnnotationName();

  private String errorMessagePrefix() {
    return "@" + getAnnotationName() + ": ";
  }

  private String getTypeName(String descriptor) {
    return Type.getType(descriptor).getClassName();
  }

  @Override
  public void visit(String name, Object value) {
    throw new KeepEdgeException(
        "Unexpected value in " + errorMessagePrefix() + name + " = " + value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String descriptor) {
    throw new KeepEdgeException(
        "Unexpected annotation in "
            + errorMessagePrefix()
            + name
            + " for annotation: "
            + getTypeName(descriptor));
  }

  @Override
  public void visitEnum(String name, String descriptor, String value) {
    throw new KeepEdgeException(
        "Unexpected enum in "
            + errorMessagePrefix()
            + name
            + " for enum: "
            + getTypeName(descriptor)
            + " with value: "
            + value);
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    throw new KeepEdgeException("Unexpected array in " + errorMessagePrefix() + name);
  }
}
