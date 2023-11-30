// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumUnboxingMappingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public EnumUnboxingMappingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(EnumKeepRules.STUDIO.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .allowDiagnosticMessages()
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertParameterTypes)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "1", "2",
            "DebugInfoForThisPrint", "1",
            "DebugInfoForThisPrint", "2");
  }

  private void assertParameterTypes(CodeInspector codeInspector) {
    ClassSubject main = codeInspector.clazz(Main.class);

    MethodSubject debugInfoMethod = main.uniqueMethodWithOriginalName("debugInfoAfterUnboxing");
    MethodSubject noDebugInfoMethod = main.uniqueMethodWithOriginalName("noDebugInfoAfterUnboxing");

    assertEquals("int", debugInfoMethod.getFinalSignature().asMethodSignature().parameters[0]);
    assertEquals("int", noDebugInfoMethod.getFinalSignature().asMethodSignature().parameters[0]);

    assertEquals(MyEnum.class.getName(), debugInfoMethod.getOriginalSignature().parameters[0]);
    // TODO(b/314076309): The original parameter should be MyEnum.class but is int.
    assertEquals("int", noDebugInfoMethod.getOriginalSignature().parameters[0]);
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(noDebugInfoAfterUnboxing(MyEnum.A));
      System.out.println(noDebugInfoAfterUnboxing(null));
      System.out.println(debugInfoAfterUnboxing(MyEnum.A));
      System.out.println(debugInfoAfterUnboxing(null));
    }

    @NeverInline
    private static int noDebugInfoAfterUnboxing(MyEnum e) {
      return (e == null ? 1 : 0) + 1;
    }

    @NeverInline
    private static int debugInfoAfterUnboxing(MyEnum e) {
      System.out.println("DebugInfoForThisPrint");
      return (e == null ? 1 : 0) + 1;
    }
  }
}
