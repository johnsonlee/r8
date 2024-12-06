// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.references.MethodReference;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress353279141 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().build();
  }

  public Regress353279141(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    runR8Compat(false)
        .inspect(
            codeInspector -> {
              MethodReference method =
                  codeInspector
                      .clazz(TestClass.class.getName() + "$1")
                      .getFinalEnclosingMethod()
                      .asMethodReference();
              assertThat(codeInspector.clazz(TestClass.class).method(method), isPresent());
            });
  }

  @Test
  public void testR8KeepIt() throws Exception {
    runR8Compat(true)
        .inspect(
            codeInspector -> {
              MethodReference method =
                  codeInspector
                      .clazz(TestClass.class.getName() + "$1")
                      .getFinalEnclosingMethod()
                      .asMethodReference();
              assertTrue(codeInspector.clazz(TestClass.class).method(method).isPresent());
            });
  }

  private R8TestRunResult runR8Compat(boolean dontOptimize)
      throws CompilationFailedException, ExecutionException, IOException {
    return testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .applyIf(dontOptimize, TestShrinkerBuilder::addDontOptimize)
        .addKeepRules(
            "-keep class **TestClass$1 { *; }", "-keepattributes InnerClasses,EnclosingMethod")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("foo");
  }

  interface RunWithIt {
    void run();

    void init();
  }

  public static class TestClass {
    public static void main(String[] args) {
      TestClass testClass = new TestClass();
      long l = System.currentTimeMillis();
      String s = l == 0 ? "x" : "y";
      int a = l == 0 ? 42 : 43;
      testClass.b(a, s);
      testClass.a(s, a);
    }

    @NeverInline
    public void a(String a, int b) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(a + b);
      }
      RunWithIt foo =
          new RunWithIt() {
            private long value;

            @Override
            public void run() {
              value = System.currentTimeMillis();
              System.out.println("foo");
            }

            @Override
            public void init() {
              value = System.currentTimeMillis();
            }
          };
      foo.init();
      foo.run();
    }

    // This method introduce a proto with opposite order, we will rewrite the a method to
    // use this order.
    @NeverInline
    public void b(int a, String s) {
      if (System.currentTimeMillis() == 0) {
        System.out.println("a value " + a + s);
      }
    }
  }
}
