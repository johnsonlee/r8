// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.horizontalclassmerging;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

@NeverClassInline
public class NestHostA {
  @NeverInline
  public NestHostA() {
    privatePrint("NestHostA");
  }

  @NeverInline
  private void privatePrint(String v) {
    System.out.println(v);
  }

  @NeverInline
  private static void privateStaticPrint(String v) {
    System.out.println(v);
  }

  @NeverClassInline
  public static class NestMemberA {
    @NeverInline
    public NestMemberA() {
      NestHostA.privateStaticPrint("NestHostA$NestMemberA");
    }
  }

  @NeverClassInline
  public static class NestMemberB {
    @NeverInline
    public NestMemberB(NestHostA host) {
      host.privatePrint("NestHostA$NestMemberB");
    }
  }
}
