// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeStaticToInterfaceVerticalClassMergerTest extends TestBase {

  @Parameter(0)
  public boolean disableInitialRoundOfVerticalClassMerging;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, disable initial: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .applyIf(
            disableInitialRoundOfVerticalClassMerging,
            b ->
                b.addOptionsModification(
                    options -> options.getVerticalClassMergerOptions().disableInitial()))
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Check interface is removed due to class merging and the static interface method is
              // present on the subclass except when moved as a result of static interface method
              // desugaring.
              ClassSubject iClassSubject = inspector.clazz(I.class);
              assertThat(iClassSubject, isAbsent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject fooMethodSubject = aClassSubject.uniqueMethodWithOriginalName("hello");
              assertThat(
                  fooMethodSubject,
                  isPresentIf(parameters.canUseDefaultAndStaticInterfaceMethods()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      // After vertical class merging, invoke should have the is-interface bit set to false.
      I.hello();
      System.out.println(new A());
    }
  }

  interface I {

    @NeverInline
    static void hello() {
      System.out.print("Hello");
    }
  }

  static class A implements I {

    @Override
    public String toString() {
      return ", world!";
    }
  }
}
