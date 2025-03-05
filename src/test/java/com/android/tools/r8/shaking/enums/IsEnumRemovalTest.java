// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.enums;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IsEnumRemovalTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED = StringUtils.lines("true", "false", "false");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IsEnumRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(IsEnumRemovalTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(IsEnumRemovalTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::inspectR8);
  }

  private void inspectR8(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(Main.class);
    assertThat(main, isPresent());
    Assert.assertEquals(
        0,
        main.uniqueMethod()
            .streamInstructions()
            .filter(i -> i.isInvoke() && i.getMethod().getName().toString().equals("isEnum"))
            .count());
  }

  public enum MyEnum {
    A {
      void foo() {
        System.out.println("aa");
      }
    },
    B {
      void foo() {
        System.out.println("bb");
      }
    };

    abstract void foo();
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(MyEnum.class.isEnum());
      System.out.println(MyEnum.B.getClass().isEnum());
      System.out.println(Set.class.isEnum());
    }
  }
}
