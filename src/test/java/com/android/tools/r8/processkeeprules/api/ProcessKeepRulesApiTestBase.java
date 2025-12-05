// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules.api;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class ProcessKeepRulesApiTestBase extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withNoneRuntime().build();
  }

  public ProcessKeepRulesApiTestBase(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  protected abstract Class<? extends ProcessKeepRulesApiBinaryTest> binaryTestClass();

  @Test
  public void testExternal() throws Exception {
    new ProcessKeepRulesApiTestCollection(temp).runJunitOnTestClass(binaryTestClass());
  }
}
