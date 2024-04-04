// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IdentityAbsorbingBoolTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "true", "true", "true", "true", "true", "true", "true", "true", "true", "true", "true",
          "true", "true", "true", "true", "false", "false", "false", "true", "true", "true",
          "false", "false", "true", "true", "false", "false", "false", "false", "false", "false",
          "false", "false", "false", "false", "false", "false", "false", "false", "false", "false",
          "false", "false", "true", "true", "true", "false", "false", "true", "true");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevels().build();
  }

  public IdentityAbsorbingBoolTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void inspect(CodeInspector inspector) {
    inspector
        .clazz(Main.class)
        .forAllMethods(
            m ->
                assertTrue(
                    m.streamInstructions()
                        .noneMatch(i -> i.isIntLogicalBinop() || i.isIntArithmeticBinop())));
  }

  static class Main {

    public static void main(String[] args) {
      boolTests(System.currentTimeMillis() > 0);
      boolTests(System.currentTimeMillis() < 0);
    }

    private static void boolTests(boolean val) {
      identityDoubleBoolTest(val);
      identityBoolTest(val);
      absorbingDoubleBoolTest(val);
      absorbingBoolTest(val);
    }

    @NeverInline
    private static void identityDoubleBoolTest(boolean val) {
      System.out.println(val & true & true);
      System.out.println(true & val & true);
      System.out.println(true & true & val);
      System.out.println(val | false | false);
      System.out.println(false | val | false);
      System.out.println(false | false | val);
      System.out.println(val ^ false ^ false);
      System.out.println(false ^ val ^ false);
      System.out.println(false ^ false ^ val);
    }

    @NeverInline
    private static void identityBoolTest(boolean val) {
      System.out.println(val & true);
      System.out.println(true & val);
      System.out.println(val | false);
      System.out.println(false | val);
      System.out.println(val ^ false);
      System.out.println(false ^ val);
    }

    @NeverInline
    private static void absorbingDoubleBoolTest(boolean val) {
      System.out.println(false & false & val);
      System.out.println(false & val & false);
      System.out.println(val & false & false);
      System.out.println(true | true | val);
      System.out.println(true | val | true);
      System.out.println(val | true | true);
    }

    @NeverInline
    private static void absorbingBoolTest(boolean val) {
      System.out.println(false & val);
      System.out.println(val & false);
      System.out.println(true | val);
      System.out.println(val | true);
    }
  }
}
