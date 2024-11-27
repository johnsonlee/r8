// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepAnnoInstanceOfTargetTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("Hello anno");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            codeInspector -> {
              // TODO(b/349276075): The rule based version should not keep less than native.
              if (!parameters.isShrinker() || parameters.isNativeR8()) {
                assertTrue(codeInspector.clazz(C.class).isPresent());
              } else {
                assertFalse(codeInspector.clazz(C.class).isPresent());
              }
            });
  }

  static class TestClass {
    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }

  static class A {
    @UsesReflection(@KeepTarget(instanceOfClassConstant = B.class, methodName = "inject"))
    public void foo() throws Exception {
      System.out.println("Hello anno");
    }
  }

  static class B {
    public static void inject() {
      System.currentTimeMillis();
    }
  }

  static class C extends B {
    public static void inject() {
      System.currentTimeMillis();
    }
  }
}
