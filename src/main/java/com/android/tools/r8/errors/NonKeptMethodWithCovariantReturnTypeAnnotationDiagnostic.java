// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;

@KeepForApi
public class NonKeptMethodWithCovariantReturnTypeAnnotationDiagnostic implements Diagnostic {

  private final Origin origin;
  private final MethodReference method;
  private final MethodPosition position;

  public NonKeptMethodWithCovariantReturnTypeAnnotationDiagnostic(ProgramMethod method) {
    this.origin = method.getOrigin();
    this.method = method.getMethodReference();
    this.position = MethodPosition.create(method);
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Methods with @CovariantReturnType annotations should be kept, but was not: "
        + MethodReferenceUtils.toSourceString(method);
  }
}
