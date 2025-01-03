// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfThatMatchesLibraryTest extends TestBase {

  @Parameter(0)
  public boolean applyIfRulesToLibrary;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        // Add a keep rule that keeps Main.main if Object is live.
        // By default -if rules are not applied to the library.
        .addKeepRules(
            "-if class java.lang.Object",
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .addOptionsModification(
            options -> {
              assertFalse(options.getTestingOptions().applyIfRulesToLibrary);
              options.getTestingOptions().applyIfRulesToLibrary = applyIfRulesToLibrary;
            })
        .allowUnusedProguardConfigurationRules(!applyIfRulesToLibrary)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector ->
                assertThat(inspector.clazz(Main.class), isPresentIf(applyIfRulesToLibrary)));
  }

  static class Main {}
}
