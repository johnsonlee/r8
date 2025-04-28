// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.lambda;

import static com.android.tools.r8.utils.AndroidApiLevel.B;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaJava17Test extends TestBase {

  private static final String EXPECTED_RESULT = "[abb, bbc]";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevels()
        .withPartialCompilation()
        .enableApiLevelsForCf()
        .build();
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Lambda.class)
        .assertSuccessWithOutputLines(EXPECTED_RESULT);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Lambda.class)
        .assertSuccessWithOutputLines(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime() || parameters.getApiLevel().equals(B));
    parameters.assumeNoPartialCompilation("TODO");
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .applyIf(
            parameters.isCfRuntime() && !parameters.getApiLevel().isEqualTo(AndroidApiLevel.B),
            // Alternatively we need to pass Jdk17 as library.
            b -> b.addKeepRules("-dontwarn java.lang.invoke.StringConcatFactory"))
        .setMinApi(parameters)
        .addKeepMainRule(Lambda.class)
        .run(parameters.getRuntime(), Lambda.class)
        .assertSuccessWithOutputLines(EXPECTED_RESULT);
  }

  public class Lambda {

    interface StringPredicate {

      boolean test(String t);

      default StringPredicate or(StringPredicate other) {
        return (t) -> test(t) || other.test(t);
      }
    }

    public static void main(String[] args) {
      ArrayList<String> strings = new ArrayList<>();
      strings.add("abc");
      strings.add("abb");
      strings.add("bbc");
      strings.add("aac");
      strings.add("acc");
      StringPredicate aaStart = Lambda::aaStart;
      StringPredicate bbNot = Lambda::bbNot;
      StringPredicate full = aaStart.or(bbNot);
      for (String string : ((List<String>) strings.clone())) {
        if (full.test(string)) {
          strings.remove(string);
        }
      }
      System.out.println(strings);
    }

    private static boolean aaStart(String str) {
      return str.startsWith("aa");
    }

    private static boolean bbNot(String str) {
      return !str.contains("bb");
    }
  }
}
