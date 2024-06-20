// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

public abstract class KeepMemberItemReference extends KeepItemReference {

  public static KeepMemberItemReference fromBindingReference(
      KeepMemberBindingReference bindingReference) {
    return new MemberBinding(bindingReference);
  }

  @Override
  public final KeepMemberItemReference asMemberItemReference() {
    return this;
  }

  private static final class MemberBinding extends KeepMemberItemReference {

    private final KeepMemberBindingReference bindingReference;

    private MemberBinding(KeepMemberBindingReference bindingReference) {
      this.bindingReference = bindingReference;
    }

    @Override
    public KeepBindingReference asBindingReference() {
      return bindingReference;
    }

    @Override
    public String toString() {
      return bindingReference.toString();
    }
  }
}
