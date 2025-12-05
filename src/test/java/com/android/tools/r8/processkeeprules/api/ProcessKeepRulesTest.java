// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules.api;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.processkeeprules.ProcessKeepRules;
import com.android.tools.r8.processkeeprules.ProcessKeepRulesCommand;
import org.junit.Test;

public class ProcessKeepRulesTest extends ProcessKeepRulesApiTestBase {

  public ProcessKeepRulesTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends ProcessKeepRulesApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testDefault() throws Exception {
    runTest();
  }

  private void runTest() throws Exception {
    new ApiTest().runProcessKeepRules();
  }

  public static class ApiTest implements ProcessKeepRulesApiBinaryTest {

    public void runProcessKeepRules() throws Exception {
      ProcessKeepRulesCommand command =
          ProcessKeepRulesCommand.builder()
              .addKeepRules("", Origin.unknown())
              .setLibraryConsumerRuleValidation(true)
              .build();
      ProcessKeepRules.run(command);
    }

    @Test
    public void testEnabled() throws Exception {
      runProcessKeepRules();
    }
  }
}
