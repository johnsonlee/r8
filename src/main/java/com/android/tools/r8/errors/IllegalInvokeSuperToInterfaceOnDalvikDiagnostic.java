// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;

@KeepForApi
public class IllegalInvokeSuperToInterfaceOnDalvikDiagnostic implements Diagnostic {

  private final MethodReference context;
  private final MethodReference invokedMethod;
  private final Origin origin;

  public IllegalInvokeSuperToInterfaceOnDalvikDiagnostic(
      MethodReference context, MethodReference invokedMethod, Origin origin) {
    this.context = context;
    this.invokedMethod = invokedMethod;
    this.origin = origin;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return MethodPosition.create(context);
  }

  @Override
  public String getDiagnosticMessage() {
    return "Verification error in `"
        + MethodReferenceUtils.toSourceString(context)
        + "`: Illegal invoke-super to interface method `"
        + MethodReferenceUtils.toSourceString(invokedMethod)
        + "` on Dalvik (Android 4).";
  }
}
