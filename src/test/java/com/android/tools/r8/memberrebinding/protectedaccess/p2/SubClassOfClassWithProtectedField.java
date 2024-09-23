// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding.protectedaccess.p2;

import com.android.tools.r8.memberrebinding.protectedaccess.p1.ClassWithProtectedField;

public class SubClassOfClassWithProtectedField extends ClassWithProtectedField {
  public SubClassOfClassWithProtectedField(int v) {
    super(v);
  }

  public void m(ClassWithProtectedField instance) {
    System.out.println(instance.f);
  }
}
