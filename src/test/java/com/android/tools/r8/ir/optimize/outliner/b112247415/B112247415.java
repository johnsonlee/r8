// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.b112247415;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class TestClass {
  interface Act {
    default String get(StringBuilder builder, String arg) {
      builder.append(arg).append(arg).append(arg);
      return builder.toString();
    }
  }

  public static void main(String[] args) {
    System.out.println(get(new TestClass().toActOverridden(), new StringBuilder(), "a"));
    System.out.println(get(new TestClass().toActDefault(), new StringBuilder(), "b"));
  }

  static String get(Act act, StringBuilder builder, String arg) {
    act.get(builder, arg);
    return builder.toString();
  }

  Act toActOverridden() {
    return new Act() {
      @Override
      public String get(StringBuilder builder, String arg) {
        builder.append(arg).append(arg).append(arg);
        return builder.toString();
      }
    };
  }

  Act toActDefault() {
    return new Act() {
    };
  }
}

@RunWith(Parameterized.class)
public class B112247415 extends TestBase {

  private static final String EXPECTED = StringUtils.lines("aaa", "bbb");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addDontObfuscate()
        .addProgramClassesAndInnerClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              // To trigger outliner, set # of expected outline candidate as threshold.
              options.outline.threshold = 2;
              options.inlinerOptions().enableInlining = false;
            })
        .collectSyntheticItems()
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              int numOutlineClasses = 0;
              for (FoundClassSubject clazz : inspector.allClasses()) {
                if (syntheticItems.isExternalOutlineClass(clazz.getFinalReference())) {
                  numOutlineClasses++;
                } else {
                  clazz.forAllMethods(
                      method ->
                          assertThat(
                              method,
                              not(invokesMethod(null, "java.lang.StringBuilder", "append", null))));
                }
              }
              assertEquals(
                  parameters.canUseDefaultAndStaticInterfaceMethods() ? 5 : 4,
                  inspector.allClasses().size());
              assertEquals(1, numOutlineClasses);
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }
}
