// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.rewrite.assertions.assertionhandler.AssertionHandlers;
import com.android.tools.r8.rewrite.assertions.assertionhandler.AssertionsSimple;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssertionConfigurationAssertionHandlerSimpleTest
    extends AssertionConfigurationAssertionHandlerTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "assertionHandler: simpleAssertion",
          "assertionHandler: multipleAssertions",
          "assertionHandler: multipleAssertions");

  @Override
  String getExpectedOutput() {
    return EXPECTED_OUTPUT;
  }

  @Override
  MethodReference getAssertionHandler() throws Exception {
    return Reference.methodFromMethod(
        AssertionHandlers.class.getMethod("assertionHandler", Throwable.class));
  }

  @Override
  List<Class<?>> getTestClasses() {
    return ImmutableList.of(AssertionsSimple.class);
  }
}
