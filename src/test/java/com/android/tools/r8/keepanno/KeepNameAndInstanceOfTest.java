// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepNameAndInstanceOfTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("on Base", "on Sub");
  static final String EXPECTED_SHRINKER = StringUtils.lines("on Base", "No method on Sub");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(TestClass.class)
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(parameters.isReference() ? EXPECTED : EXPECTED_SHRINKER);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, Base.class, Sub.class, A.class);
  }

  static class Base {

    static void hiddenMethod() {
      System.out.println("on Base");
    }
  }

  static class Sub extends Base {

    static void hiddenMethod() {
      System.out.println("on Sub");
    }
  }

  static class A {

    @UsesReflection({
      @KeepTarget(
          // Restricting the matching to Base will cause Sub to be stripped.
          classConstant = Base.class,
          instanceOfClassConstant = Base.class,
          methodName = "hiddenMethod")
    })
    public void foo(Base base) throws Exception {
      base.getClass().getDeclaredMethod("hiddenMethod").invoke(null);
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      try {
        new A().foo(new Base());
      } catch (Exception e) {
        System.out.println("No method on Base");
      }
      try {
        new A().foo(new Sub());
      } catch (Exception e) {
        System.out.println("No method on Sub");
      }
    }
  }
}
