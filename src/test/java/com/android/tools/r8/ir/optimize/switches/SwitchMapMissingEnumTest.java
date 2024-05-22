// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.switches;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
public class SwitchMapMissingEnumTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private Collection<byte[]> getInnerClassesWithoutEnum() throws IOException {
    return ToolHelper.getClassFilesForInnerClasses(getClass()).stream()
        .filter(c -> !c.toString().endsWith("MyEnum.class"))
        .map(
            c -> {
              try {
                return transformer(c, null)
                    .transformMethodInsnInMethod(
                        MethodPredicate.all(),
                        (opcode, owner, name, descriptor, isInterface, visitor) -> {
                          // Rewrites all the ordinal calls into Enum#ordinal() from
                          // MyEnum#ordinal().
                          if (name.equals("ordinal")) {
                            visitor.visitMethodInsn(
                                opcode, "java/lang/Enum", name, descriptor, isInterface);
                          } else {
                            visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                          }
                        })
                    .transform();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassFileData(getInnerClassesWithoutEnum())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntimeVersionOlderThanOrEqual(Version.V4_4_4),
            b -> b.assertFailureWithErrorThatThrows(VerifyError.class),
            b -> b.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .addProgramClassFileData(getInnerClassesWithoutEnum())
        .addOptionsModification(opt -> opt.ignoreMissingClasses = true)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntimeVersionOlderThanOrEqual(Version.V4_4_4),
            b -> b.assertFailureWithErrorThatThrows(VerifyError.class),
            b -> b.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B;
  }

  public static class Main {

    public static void main(String[] args) {
      print(MyEnum.A);
      print(MyEnum.B);
    }

    private static void print(MyEnum e) {
      switch (e) {
        case A:
          System.out.println("a");
          break;
        case B:
          System.out.println("b");
          break;
        default:
          System.out.println("0");
      }
    }
  }
}
