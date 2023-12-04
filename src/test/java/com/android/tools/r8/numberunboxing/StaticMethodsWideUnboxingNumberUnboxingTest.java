// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticMethodsWideUnboxingNumberUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StaticMethodsWideUnboxingNumberUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addOptionsModification(opt -> opt.testing.enableNumberUnboxer = true)
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("32", "33", "42", "43", "52", "53", "2");
  }

  private void assertSecondParameterUnboxed(ClassSubject mainClass, String methodName) {
    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(1).isLongType());
  }

  private void assertSecondParameterBoxed(ClassSubject mainClass, String methodName) {
    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(1).isReferenceType());
  }

  private void assertReturnUnboxed(ClassSubject mainClass, String methodName) {
    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getReturnType().isLongType());
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    ClassSubject mainClass = codeInspector.clazz(Main.class);
    assertThat(mainClass, isPresent());

    assertSecondParameterUnboxed(mainClass, "print");
    assertSecondParameterUnboxed(mainClass, "forwardToPrint2");
    assertSecondParameterUnboxed(mainClass, "directPrintUnbox");
    assertSecondParameterUnboxed(mainClass, "forwardToPrint");

    assertReturnUnboxed(mainClass, "get");
    assertReturnUnboxed(mainClass, "forwardGet");

    assertSecondParameterBoxed(mainClass, "directPrintNotUnbox");
  }

  static class Main {

    public static void main(String[] args) {
      long shift = System.currentTimeMillis() > 0 ? 1L : 0L;

      // The number unboxer should immediately find this method is worth unboxing.
      directPrintUnbox(shift, 31L);
      directPrintUnbox(shift, 32L);

      // The number unboxer should find the chain of calls is worth unboxing.
      forwardToPrint(shift, 41L);
      forwardToPrint(shift, 42L);

      // The number unboxer should find this method is *not* worth unboxing.
      Long decode1 = Long.decode("51");
      Objects.requireNonNull(decode1);
      directPrintNotUnbox(shift, decode1);
      Long decode2 = Long.decode("52");
      Objects.requireNonNull(decode2);
      directPrintNotUnbox(shift, decode2);

      // The number unboxer should unbox the return values.
      System.out.println(forwardGet() + 1);
    }

    @NeverInline
    private static Long get() {
      return System.currentTimeMillis() > 0 ? 1L : -1L;
    }

    @NeverInline
    private static Long forwardGet() {
      return get();
    }

    @NeverInline
    private static void forwardToPrint(long shift, Long boxed) {
      forwardToPrint2(shift, boxed);
    }

    @NeverInline
    private static void forwardToPrint2(long shift, Long boxed) {
      print(shift, boxed);
    }

    @NeverInline
    private static void print(long shift, Long boxed) {
      System.out.println(boxed + shift);
    }

    @NeverInline
    private static void directPrintUnbox(long shift, Long boxed) {
      System.out.println(boxed + shift);
    }

    @NeverInline
    private static void directPrintNotUnbox(long shift, Long boxed) {
      Long newBox = Long.valueOf(boxed + shift);
      System.out.println(newBox);
    }
  }
}
