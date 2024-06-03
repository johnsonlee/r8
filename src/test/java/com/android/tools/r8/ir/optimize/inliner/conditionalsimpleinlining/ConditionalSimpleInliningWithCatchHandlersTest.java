// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner.conditionalsimpleinlining;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConditionalSimpleInliningWithCatchHandlersTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject doStuffMethodSubject =
                  inspector.clazz(Utils.class).uniqueMethodWithOriginalName("doStuff");
              assertThat(doStuffMethodSubject, isPresent());

              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertEquals(
                  6,
                  mainMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isInvokeMethod)
                      .filter(
                          x ->
                              x.getMethod()
                                  .isIdenticalTo(doStuffMethodSubject.getMethod().getReference()))
                      .count());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      Object o = get();

      // Should not be inlined since the catch handler is not simple.
      if (o != null) {
        Utils.doStuff(o);
      }

      // Should not be inlined since the simple inlining constraint is not satisfied.
      Utils.doStuff(o);
      Utils.doStuff(o);
      Utils.doStuff(o);
      Utils.doStuff(o);
      Utils.doStuff(o);
    }

    static Object get() {
      return new Object();
    }
  }

  static class Utils {

    static void doStuff(Object o) {
      if (o != null) {
        try {
          o.toString();
        } catch (Exception e) {
          System.out.print('N');
          System.out.print('o');
          System.out.print('t');
          System.out.print(' ');
          System.out.print('s');
          System.out.print('i');
          System.out.print('m');
          System.out.print('p');
          System.out.print('l');
          System.out.print('e');
        }
      } else {
        System.out.print('N');
        System.out.print('o');
        System.out.print('t');
        System.out.print(' ');
        System.out.print('s');
        System.out.print('i');
        System.out.print('m');
        System.out.print('p');
        System.out.print('l');
        System.out.print('e');
      }
    }
  }
}
