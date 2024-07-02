// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class KeepInclusiveInstanceOfTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("on Base", "on Sub");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(TestClass.class)
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(
            parameters.isShrinker(),
            r -> r.inspect(inspector -> assertThat(inspector.clazz(Unrelated.class), isAbsent())));
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, Base.class, Sub.class, A.class, Unrelated.class);
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

  static class Unrelated {
    static void hiddenMethod() {
      System.out.println("on Unrelated");
    }
  }

  static class A {

    @UsesReflection({
      // Because the method is static, this works whereas `classConstant = Base.class` won't
      // keep the method on `Sub`.
      @KeepTarget(instanceOfClassConstant = Base.class, methodName = "hiddenMethod")
    })
    public void foo(Base base) throws Exception {
      base.getClass().getDeclaredMethod("hiddenMethod").invoke(null);
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo(new Base());
      new A().foo(new Sub());
    }
  }
}
