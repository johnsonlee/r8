// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.B419404081Test.Cr.Ci_abs_sta;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B419404081Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean withKeepRules;

  @Parameters(name = "{0}, withKeepRules = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("123");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(!withKeepRules);
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Cr.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(!withKeepRules);
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Cr.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Cr.class)
        // b/419404081 also had keep member rules for Cr, but that was not needed for the
        // reproduction.
        .applyIf(withKeepRules, b -> b.addKeepClassAndMembersRules(Ci_abs_sta.class))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Cr.class)
        .applyIf(
            withKeepRules,
            r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class),
            b -> b.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  static class Cr {
    public static void main(String[] args) {
      Ci_sta v0 = new Ci_sta();
      java.lang.System.out.println(Cr.method(v0));
    }

    static String method(Ii_1 arg1) {
      arg1.method1();
      return "123";
    }

    interface Ii_1 {
      void method1();
    }

    interface Ii_2 extends Ii_1 {
      default void method1() {}
    }

    abstract static class Ci_abs_sta implements Ii_1 {}

    static class Ci_sta extends Ci_abs_sta implements Ii_2 {}
  }
}
