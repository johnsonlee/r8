// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.MethodInvokeRewriter;

public final class CollectionMethodRewrites {

  private CollectionMethodRewrites() {}

  public static MethodInvokeRewriter REWRITE_EMPTY_LIST =
      (invoke, factory) -> new CfStaticFieldRead(factory.javaUtilCollectionsMembers.EMPTY_LIST);

  public static MethodInvokeRewriter REWRITE_EMPTY_MAP =
      (invoke, factory) -> new CfStaticFieldRead(factory.javaUtilCollectionsMembers.EMPTY_MAP);

  public static MethodInvokeRewriter REWRITE_EMPTY_SET =
      (invoke, factory) -> new CfStaticFieldRead(factory.javaUtilCollectionsMembers.EMPTY_SET);
}
