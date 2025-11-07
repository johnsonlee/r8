// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexAddInt;
import com.android.tools.r8.dex.code.DexAddInt2Addr;
import com.android.tools.r8.dex.code.DexMulInt;
import com.android.tools.r8.dex.code.DexMulInt2Addr;
import com.android.tools.r8.dex.code.DexReturn;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultipleOutlinesInMethodWithExceptionHandlerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("18");

  private void validateOutlining(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    // Validate that an outline of mul, mul, add has been created and called twice in m.
    ClassSubject outlineClass =
        inspector.clazz(syntheticItems.syntheticOutlineClass(TestClass.class, 0));
    assertThat(outlineClass, isPresent());
    MethodSubject outline0Method =
        outlineClass.method(
            "int",
            SyntheticItemsTestUtils.syntheticMethodName(),
            ImmutableList.of("int", "int", "int", "int"));
    assertThat(outline0Method, isPresent());
    // Only check the content if instructions fo DEX.
    if (parameters.isDexRuntime()) {
      Map<Class<?>, Class<?>> map =
          ImmutableMap.of(
              DexMulInt2Addr.class, DexMulInt.class, DexAddInt2Addr.class, DexAddInt.class);
      List<Class<?>> instructionClasses =
          outline0Method
              .streamInstructions()
              .map(instruction -> instruction.asDexInstruction().getInstruction().getClass())
              .map(instructionClass -> map.getOrDefault(instructionClass, instructionClass))
              .collect(Collectors.toList());
      assertEquals(
          ImmutableList.of(DexMulInt.class, DexMulInt.class, DexAddInt.class, DexReturn.class),
          instructionClasses);
    }
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("m");
    List<InstructionSubject> outlineInvokes =
        methodSubject
            .streamInstructions()
            .filter(CodeMatchers.isInvokeWithTarget(outline0Method))
            .collect(Collectors.toList());
    assertEquals(2, outlineInvokes.size());
    // Check that both outlines invoked are covered by the catch handler.
    methodSubject
        .iterateTryCatches()
        .forEachRemaining(
            tryCatchSubject -> {
              outlineInvokes.removeIf(
                  instructionSubject ->
                      tryCatchSubject
                          .getRange()
                          .includes(instructionSubject.getOffset(methodSubject)));
            });
    assertTrue(outlineInvokes.isEmpty());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .addOptionsModification(
            options -> {
              // To trigger outlining.
              options.outline.threshold = 2;
              options.outline.maxSize = 3;
            })
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(this::validateOutlining)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {

    @NeverInline
    private static void m(int a, int b, int c, int d) {
      try {
        // The test is expecting an outline with mul, mul, add instructions.
        System.out.println((a * b * c + d) * (d * c * b + a));
      } catch (ArithmeticException e) {
        System.out.println(e);
      }
    }

    public static void main(String[] args) {
      m(args.length, args.length + 1, args.length + 2, args.length + 3);
    }
  }
}
