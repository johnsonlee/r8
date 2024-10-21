// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineConstructorWithFinalFieldsTest extends TestBase {

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
        // Use most recent android.jar so that VarHandle is present.
        .applyIf(
            parameters.isDexRuntime(),
            testBuilder -> testBuilder.addLibraryFiles(ToolHelper.getMostRecentAndroidJar()))
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              assertFalse(options.inlinerOptions().enableConstructorInliningWithFinalFields);
              options.inlinerOptions().enableConstructorInliningWithFinalFields = true;
            })
        .enableAlwaysInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject initMethodSubject = mainClassSubject.init("int", "int");
              assertThat(
                  initMethodSubject,
                  isAbsentIf(
                      parameters.isDexRuntime()
                          && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.P)));

              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              if (initMethodSubject.isPresent()) {
                assertThat(mainMethodSubject, invokesMethod(initMethodSubject));
              } else {
                assertThat(
                    mainMethodSubject,
                    invokesMethod(MethodReferenceUtils.instanceConstructor(Object.class)));
                assertThat(
                    mainMethodSubject,
                    invokesMethod(
                        Reference.methodFromDescriptor(
                            "Ljava/lang/invoke/VarHandle;", "storeStoreFence", "()V")));
              }
            })
        .run(parameters.getRuntime(), Main.class, "20", "22")
        .assertSuccessWithOutputLines("42");
  }

  static class Main {

    final int x;
    final int y;

    @AlwaysInline
    Main(int x, int y) {
      this.x = x;
      this.y = y;
    }

    public static void main(String[] args) {
      Main main = new Main(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
      System.out.println(main);
    }

    @Override
    public String toString() {
      return Integer.toString(x + y);
    }
  }
}
