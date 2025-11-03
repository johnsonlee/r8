// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TimeUnitTest extends TestBase {

  private final String[] EXPECTED =
      new String[] {
        "2",
        "3",
        "4",
        "5",
        "6",
        "0",
        "0",
        "2",
        "3",
        "4",
        "5",
        "6",
        "0",
        "0",
        "48",
        "3",
        "4",
        "5",
        "6",
        "0",
        "0",
        "3",
        "4",
        "5",
        "6",
        "0",
        "0",
        "2880",
        "180",
        "4",
        "5",
        "6",
        "8",
        "0",
        "4",
        "5",
        "6",
        "8",
        "0",
        "172800",
        "10800",
        "240",
        "5",
        "6",
        "8",
        "0",
        "5",
        "6",
        "8",
        "0",
        "172800000",
        "10800000",
        "240000",
        "5000",
        "6",
        "7",
        "8",
        "6",
        "7",
        "8",
        "172800000000",
        "10800000000",
        "240000000",
        "5000000",
        "6000",
        "7",
        "8",
        "7",
        "8",
        "172800000000000",
        "10800000000000",
        "240000000000",
        "5000000000",
        "6000000",
        "7000",
        "8",
        "8",
        "1",
        "2",
        "1000",
        "1000",
        "9000",
        "1",
        "2",
      };

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public TimeUnitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(TimeUnitTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    testClassSubject.forAllMethods(
        method -> {
          long numExpectedFieldGets = 0;
          String name = method.getOriginalMethodName();
          if (name.contains("_")) {
            numExpectedFieldGets = Long.valueOf(name.split("_")[1]);
          }
          long actualGets =
              method.streamInstructions().filter(TimeUnitTest::isTimeUnitGetter).count();
          if (numExpectedFieldGets != actualGets) {
            assertEquals(
                method.getOriginalMethodName() + "\n" + method.getMethod().codeToString(),
                numExpectedFieldGets,
                actualGets);
          }
        });
  }

  private static boolean isTimeUnitGetter(InstructionSubject ins) {
    return ins.isStaticGet()
        && ((FieldAccessInstructionSubject) ins)
            .holder()
            .getTypeName()
            .equals("java.util.concurrent.TimeUnit");
  }

  static class TestClass {

    public static void main(String[] args) {
      testToDays();
      testToHours();
      testToMinutes();
      testToSeconds();
      testToMillis();
      testToMicros();
      testToNanos();
      testConvert();
      testDoNotOptimizeSaturatedMultiply_2();
      testSharedTimeUnit_2();
      testPhi_2();
    }

    @NeverInline
    static void testToDays() {
      TimeUnit days = TimeUnit.DAYS;
      System.out.println(days.toDays(2));
      System.out.println(TimeUnit.HOURS.toDays(95));
      System.out.println(TimeUnit.MINUTES.toDays(5 * 24 * 60 - 1));
      System.out.println(TimeUnit.SECONDS.toDays(6 * 24 * 60 * 60 - 1));
      System.out.println(TimeUnit.MILLISECONDS.toDays(7 * 24 * 60 * 60 * 1000 - 1));
      System.out.println(TimeUnit.MICROSECONDS.toDays(9 * 24 * 60 * 60 * 1000000 - 1));
      System.out.println(TimeUnit.NANOSECONDS.toDays(8 * 24 * 60 * 60 * 1000000000 - 1));

      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(days.toDays(3 - value));
      System.out.println(TimeUnit.HOURS.toDays(4 * 24 - value));
      System.out.println(TimeUnit.MINUTES.toDays(5 * 24 * 60 - value));
      System.out.println(TimeUnit.SECONDS.toDays(6 * 24 * 60 * 60 - value));
      System.out.println(TimeUnit.MILLISECONDS.toDays(7 * 24 * 60 * 60 * 1000 - value));
      System.out.println(TimeUnit.MICROSECONDS.toDays(9 * 24 * 60 * 60 * 1000000 - value));
      System.out.println(TimeUnit.NANOSECONDS.toDays(8 * 24 * 60 * 60 * 1000000000 - value));
    }

    @NeverInline
    static void testToHours() {
      System.out.println(TimeUnit.DAYS.toHours(3 - 1));
      System.out.println(TimeUnit.HOURS.toHours(4 - 1));
      System.out.println(TimeUnit.MINUTES.toHours(5 * 60 - 1));
      System.out.println(TimeUnit.SECONDS.toHours(6 * 60 * 60 - 1));
      System.out.println(TimeUnit.MILLISECONDS.toHours(7 * 60 * 60 * 1000 - 1));
      System.out.println(TimeUnit.MICROSECONDS.toHours(9 * 60 * 60 * 1000000 - 1));
      System.out.println(TimeUnit.NANOSECONDS.toHours(8 * 60 * 60 * 1000000000 - 1));

      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(TimeUnit.HOURS.toHours(4 - value));
      System.out.println(TimeUnit.MINUTES.toHours(5 * 60 - value));
      System.out.println(TimeUnit.SECONDS.toHours(6 * 60 * 60 - value));
      System.out.println(TimeUnit.MILLISECONDS.toHours(7 * 60 * 60 * 1000 - value));
      System.out.println(TimeUnit.MICROSECONDS.toHours(9 * 60 * 60 * 1000000 - value));
      System.out.println(TimeUnit.NANOSECONDS.toHours(8 * 60 * 60 * 1000000000 - value));
    }

    @NeverInline
    static void testToMinutes() {
      System.out.println(TimeUnit.DAYS.toMinutes(3 - 1));
      System.out.println(TimeUnit.HOURS.toMinutes(4 - 1));
      System.out.println(TimeUnit.MINUTES.toMinutes(5 - 1));
      System.out.println(TimeUnit.SECONDS.toMinutes(6 * 60 - 1));
      System.out.println(TimeUnit.MILLISECONDS.toMinutes(7 * 60 * 1000 - 1));
      System.out.println(TimeUnit.MICROSECONDS.toMinutes(9 * 60 * 1000000 - 1));
      System.out.println(TimeUnit.NANOSECONDS.toMinutes(8 * 60 * 1000000000 - 1));

      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(TimeUnit.MINUTES.toMinutes(5 - value));
      System.out.println(TimeUnit.SECONDS.toMinutes(6 * 60 - value));
      System.out.println(TimeUnit.MILLISECONDS.toMinutes(7 * 60 * 1000 - value));
      System.out.println(TimeUnit.MICROSECONDS.toMinutes(9 * 60 * 1000000 - value));
      System.out.println(TimeUnit.NANOSECONDS.toMinutes(8 * 60 * 1000000000 - value));
    }

    @NeverInline
    static void testToSeconds() {
      System.out.println(TimeUnit.DAYS.toSeconds(3 - 1));
      System.out.println(TimeUnit.HOURS.toSeconds(4 - 1));
      System.out.println(TimeUnit.MINUTES.toSeconds(5 - 1));
      System.out.println(TimeUnit.SECONDS.toSeconds(6 - 1));
      System.out.println(TimeUnit.MILLISECONDS.toSeconds(7 * 1000 - 1));
      System.out.println(TimeUnit.MICROSECONDS.toSeconds(9 * 1000000 - 1));
      System.out.println(TimeUnit.NANOSECONDS.toSeconds(8 * 1000000000 - 1));

      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(TimeUnit.SECONDS.toSeconds(6 - value));
      System.out.println(TimeUnit.MILLISECONDS.toSeconds(7 * 1000 - value));
      System.out.println(TimeUnit.MICROSECONDS.toSeconds(9 * 1000000 - value));
      System.out.println(TimeUnit.NANOSECONDS.toSeconds(8 * 1000000000 - value));
    }

    @NeverInline
    static void testToMillis() {
      System.out.println(TimeUnit.DAYS.toMillis(3 - 1));
      System.out.println(TimeUnit.HOURS.toMillis(4 - 1));
      System.out.println(TimeUnit.MINUTES.toMillis(5 - 1));
      System.out.println(TimeUnit.SECONDS.toMillis(6 - 1));
      System.out.println(TimeUnit.MILLISECONDS.toMillis(7 - 1));
      System.out.println(TimeUnit.MICROSECONDS.toMillis(8000 - 1));
      System.out.println(TimeUnit.NANOSECONDS.toMillis(9000000 - 1));

      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(TimeUnit.MILLISECONDS.toMillis(7 - value));
      System.out.println(TimeUnit.MICROSECONDS.toMillis(8000 - value));
      System.out.println(TimeUnit.NANOSECONDS.toMillis(9000000 - value));
    }

    @NeverInline
    static void testToMicros() {
      System.out.println(TimeUnit.DAYS.toMicros(3 - 1));
      System.out.println(TimeUnit.HOURS.toMicros(4 - 1));
      System.out.println(TimeUnit.MINUTES.toMicros(5 - 1));
      System.out.println(TimeUnit.SECONDS.toMicros(6 - 1));
      System.out.println(TimeUnit.MILLISECONDS.toMicros(7 - 1));
      System.out.println(TimeUnit.MICROSECONDS.toMicros(8 - 1));
      System.out.println(TimeUnit.NANOSECONDS.toMicros(9000 - 1));

      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(TimeUnit.MICROSECONDS.toMicros(8 - value));
      System.out.println(TimeUnit.NANOSECONDS.toMicros(9000 - value));
    }

    @NeverInline
    static void testToNanos() {
      System.out.println(TimeUnit.DAYS.toNanos(2));
      System.out.println(TimeUnit.HOURS.toNanos(3));
      System.out.println(TimeUnit.MINUTES.toNanos(4));
      System.out.println(TimeUnit.SECONDS.toNanos(5));
      System.out.println(TimeUnit.MILLISECONDS.toNanos(6));
      System.out.println(TimeUnit.MICROSECONDS.toNanos(7));
      System.out.println(TimeUnit.NANOSECONDS.toNanos(8));

      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(TimeUnit.NANOSECONDS.toNanos(9 - value));
    }

    @NeverInline
    static void testConvert() {
      System.out.println(TimeUnit.MINUTES.convert(119, TimeUnit.SECONDS));

      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(TimeUnit.HOURS.convert(119 + value, TimeUnit.MINUTES));
    }

    @NeverInline
    static void testDoNotOptimizeSaturatedMultiply_2() {
      long value = System.currentTimeMillis() > 0 ? 1 : 0;
      // Do not optimize values that may require a saturated multiplication of a non-constant
      // value(which Java does not provide).
      System.out.println(TimeUnit.MILLISECONDS.convert(value, TimeUnit.SECONDS));
    }

    @NeverInline
    static void testSharedTimeUnit_2() {
      TimeUnit dstUnit = TimeUnit.MILLISECONDS;
      TimeUnit srcUnit = TimeUnit.SECONDS;
      System.out.println(dstUnit.convert(System.currentTimeMillis() > 0 ? 1 : 0, srcUnit));
      System.out.println(dstUnit.convert(9, srcUnit));
    }

    @NeverInline
    static void testPhi_2() {
      boolean cond = System.currentTimeMillis() > 0;
      TimeUnit unit = cond ? TimeUnit.SECONDS : TimeUnit.MILLISECONDS;
      System.out.println(unit.convert(1, unit));
      System.out.println(unit.toSeconds(cond ? 2 : 0));
    }
  }
}
