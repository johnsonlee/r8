// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.Keep;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EmptyVarargsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EmptyVarargsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAnnotation()
        .addKeepRules("-keepclassmembers class * { @com.android.tools.r8.Keep *; }")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject testMethod =
                  mainClassSubject.uniqueMethodWithOriginalName("testDeclared");
              assertTrue(testMethod.streamInstructions().anyMatch(InstructionSubject::isNewArray));
              testMethod = mainClassSubject.uniqueMethodWithOriginalName("testNonDeclared");
              assertTrue(testMethod.streamInstructions().anyMatch(InstructionSubject::isNewArray));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("hi", "hi");
  }

  static class Main {
    public static void main(String[] args) {
      try {
        testDeclared();
        testNonDeclared();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Keep
    public Main() {}

    @Keep
    public void doPrint() {
      System.out.println("hi");
    }

    @NeverInline
    static void testNonDeclared() throws Exception {
      Constructor<Main> ctor = Main.class.getConstructor();
      Main instance = ctor.newInstance();
      Method method = Main.class.getMethod("doPrint");
      method.invoke(instance);
    }

    @NeverInline
    static void testDeclared() throws Exception {
      Constructor<Main> ctor = Main.class.getDeclaredConstructor();
      Main instance = ctor.newInstance();
      Method method = Main.class.getDeclaredMethod("doPrint");
      method.invoke(instance);
    }
  }
}
