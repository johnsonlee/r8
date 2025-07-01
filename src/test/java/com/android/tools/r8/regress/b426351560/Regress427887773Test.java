// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b426351560;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.regress.b426351560.testclasses.Regress426351560TestClasses;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress427887773Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters)
          .addInnerClasses(getClass())
          .addClasspathClasses(Regress426351560TestClasses.getClasses())
          .addKeepRules("-keep class " + Main.class.getTypeName() + " { *; }")
          .compile()
          .addRunClasspathClasses(Regress426351560TestClasses.getClasses())
          .run(parameters.getRuntime(), Main.class)
          .applyIf(
              parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
              rr -> rr.assertFailureWithErrorThatThrows(IllegalAccessError.class),
              // for jdk based tests: java.lang.VerifyError: Bad invokespecial instruction:
              // interface method reference is in an indirect superinterface.
              rr -> rr.assertFailureWithErrorThatThrows(VerifyError.class));
    } catch (CompilationFailedException e) {
      if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
        assertTrue(e.getCause() instanceof AssertionError);
        assertThat(e.getCause().getMessage(), containsString("CC was already present."));
      } else {
        throw e;
      }
    }
  }

  public static class Main extends Regress426351560TestClasses.A {
    public static void main(String[] strArr) {
      Main test = new Main();
      System.out.println(test.defaultMethod());
    }
  }
}
