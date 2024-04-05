// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ComposeBinopTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "12345678",
          "12345678",
          "0",
          "0",
          "76532",
          "76532",
          "0",
          "0",
          "-2147483648",
          "-2147483648",
          "0",
          "0",
          "74",
          "0",
          "12345838",
          "12345854",
          "240",
          "160",
          "76532",
          "76542",
          "0",
          "0",
          "-2147483488",
          "-2147483398",
          "8260",
          "0",
          "12345678",
          "-2135069698",
          "8260",
          "0",
          "76532",
          "-2135069698",
          "0",
          "0",
          "-2147475388",
          "-2135069698",
          "0",
          "0",
          "3948544",
          "1572864",
          "0",
          "0",
          "0",
          "0",
          "252706816",
          "100663296",
          "0",
          "0",
          "255",
          "80",
          "241",
          "96",
          "243",
          "96",
          "1551743",
          "1032",
          "99311600",
          "66080",
          "1551743",
          "1032",
          "-266892247",
          "0",
          "98765424",
          "0",
          "269978665",
          "0",
          "-268425890",
          "0",
          "612256",
          "0",
          "268445022",
          "0",
          "12413950",
          "8260",
          "12413950",
          "8260",
          "12413950",
          "8260",
          "-131068",
          "0",
          "1253900288",
          "0",
          "131076",
          "0",
          "-2037",
          "0",
          "350224384",
          "0",
          "2059",
          "0",
          "41",
          "350",
          "0");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevels().build();
  }

  public ComposeBinopTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertTrue(mainClass.isPresent());

    assertTrue(
        mainClass
            .uniqueMethodWithOriginalName("bitSameInput")
            .streamInstructions()
            .noneMatch(InstructionSubject::isIntLogicalBinop));
    assertEquals(
        6,
        mainClass
            .uniqueMethodWithOriginalName("shareShiftCstLeft")
            .streamInstructions()
            .filter(InstructionSubject::isIntLogicalBinop)
            .count());
    assertEquals(
        12,
        mainClass
            .uniqueMethodWithOriginalName("shareShiftCstRight")
            .streamInstructions()
            .filter(InstructionSubject::isIntLogicalBinop)
            .count());
    assertEquals(
        12,
        mainClass
            .uniqueMethodWithOriginalName("shareShiftVar")
            .streamInstructions()
            .filter(InstructionSubject::isIntLogicalBinop)
            .count());
    assertEquals(
        4,
        mainClass
            .uniqueMethodWithOriginalName("andOrCompositionCst")
            .streamInstructions()
            .filter(InstructionSubject::isIntLogicalBinop)
            .count());
    assertEquals(
        8,
        mainClass
            .uniqueMethodWithOriginalName("andOrCompositionVar")
            .streamInstructions()
            .filter(InstructionSubject::isIntLogicalBinop)
            .count());
    assertEquals(
        2,
        mainClass
            .uniqueMethodWithOriginalName("composeTests")
            .streamInstructions()
            .filter(InstructionSubject::isIntLogicalBinop)
            .count());
  }

  static class Main {

    public static void main(String[] args) {
      int i1 = System.currentTimeMillis() > 0 ? 12345678 : 1;
      int i2 = System.currentTimeMillis() > 0 ? 76532 : 1;
      int i3 = System.currentTimeMillis() > 0 ? Integer.MIN_VALUE : 1;

      bitSameInput(i1);
      bitSameInput(i2);
      bitSameInput(i3);

      andOrCompositionCst(i1);
      andOrCompositionCst(i2);
      andOrCompositionCst(i3);

      andOrCompositionVar(i1, i2, i3);
      andOrCompositionVar(i2, i3, i1);
      andOrCompositionVar(i3, i1, i2);

      shareShiftCstLeft(i1);
      shareShiftCstLeft(i2);
      shareShiftCstLeft(i3);

      shareShiftCstRight(i1, i2);
      shareShiftCstRight(i1, i3);
      shareShiftCstRight(i2, i3);

      shareShiftVar(i1, i2, i3);
      shareShiftVar(i2, i3, i1);
      shareShiftVar(i3, i1, i2);

      composeTests(i1);
      composeTests(i2);
      composeTests(i3);
    }

    @NeverInline
    private static void bitSameInput(int a) {
      // a & a => a, a | a => a.
      System.out.println(a & a);
      System.out.println(a | a);
      // a ^ a => 0, a - a => 0
      System.out.println(a ^ a);
      System.out.println(a - a);
    }

    @NeverInline
    private static void shareShiftCstRight(int a, int b) {
      // (x shift: val) | (y shift: val) => (x | y) shift: val.
      // (x shift: val) & (y shift: val) => (x & y) shift: val.
      System.out.println((a >> 3) | (b >> 3));
      System.out.println((a >> 3) & (b >> 3));
      System.out.println((a << 3) | (b << 3));
      System.out.println((a << 3) & (b << 3));
      System.out.println((a >>> 3) | (b >>> 3));
      System.out.println((a >>> 3) & (b >>> 3));
    }

    @NeverInline
    private static void shareShiftCstLeft(int a) {
      // (x shift: val) | (y shift: val) => (x | y) shift: val.
      // (x shift: val) & (y shift: val) => (x & y) shift: val.
      System.out.println((111 >> a) | (222 >> a));
      System.out.println((112 >> a) & (223 >> a));
      System.out.println((113 << a) | (224 << a));
      System.out.println((114 << a) & (225 << a));
      System.out.println((115 >>> a) | (226 >>> a));
      System.out.println((116 >>> a) & (227 >>> a));
    }

    @NeverInline
    private static void shareShiftVar(int a, int b, int c) {
      // (x shift: val) | (y shift: val) => (x | y) shift: val.
      // (x shift: val) & (y shift: val) => (x & y) shift: val.
      System.out.println((a >> c) | (b >> c));
      System.out.println((a >> c) & (b >> c));
      System.out.println((a << c) | (b << c));
      System.out.println((a << c) & (b << c));
      System.out.println((a >>> c) | (b >>> c));
      System.out.println((a >>> c) & (b >>> c));
    }

    @NeverInline
    private static void andOrCompositionCst(int a) {
      // For all permutations of & and |, represented by &| and |&.
      // (x &| a) |& (x &| b) => x &| (a |& b).
      // Note that here a and b are constants therefore should be resolved at compile-time.
      System.out.println((a & 0b10101010) | (a & 0b11110000));
      System.out.println((a & 0b10101010) & (a & 0b11110000));
      System.out.println((a | 0b10101010) & (a | 0b11110000));
      System.out.println((a | 0b10101010) | (a | 0b11110000));
    }

    @NeverInline
    private static void andOrCompositionVar(int a, int b, int c) {
      // For all permutations of & and |.
      // (x &| a) |& (x &| b) => x &| (a |& b).
      System.out.println((a & b) | (a & c));
      System.out.println((a & b) & (a & c));
      System.out.println((a | b) & (a | c));
      System.out.println((a | b) | (a | c));
    }

    @NeverInline
    private static void composeTests(int dirty) {
      // This is rewritten to ((0b0001111111111110 & dirty) >> 3).
      System.out.println(
          ((0b1110 & dirty) >> 3)
              | ((0b01110000 & dirty) >> 3)
              | ((0b001110000000 & dirty) >> 3)
              | ((0b0001110000000000 & dirty) >> 3));
    }
  }
}
