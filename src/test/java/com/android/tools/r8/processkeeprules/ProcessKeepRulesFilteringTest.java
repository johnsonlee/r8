// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.processkeeprules;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProcessKeepRulesFilteringTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    String keepRules =
        StringUtils.lines(
            " -dontobfuscate -dontoptimize -dontshrink # remove",
            "# Keep all attributes",
            "-keepattributes *",
            "# Keep all",
            "-keep  class **");
    FilteredKeepRules filteredKeepRules = new FilteredKeepRules();
    ProcessKeepRulesCommand command =
        ProcessKeepRulesCommand.builder(diagnostics)
            .addKeepRules(keepRules, Origin.unknown())
            .setFilteredKeepRulesConsumer(filteredKeepRules)
            .build();
    ProcessKeepRules.run(command);
    assertEquals(keepRules, filteredKeepRules.get());
    diagnostics.assertNoMessages();
  }

  private static class FilteredKeepRules implements StringConsumer {

    private StringBuilder sb = new StringBuilder();
    private String result;

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      sb.append(string);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      result = sb.toString();
      sb = null;
    }

    public String get() {
      return result;
    }
  }
}
