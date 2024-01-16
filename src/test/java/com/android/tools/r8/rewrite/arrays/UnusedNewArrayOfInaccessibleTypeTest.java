// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class UnusedNewArrayOfInaccessibleTypeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeCfRuntime();
    testForJvm(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(getProgramClassFileData())
        .release()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .enableNoAccessModificationAnnotationsForClasses()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  private static List<byte[]> getProgramClassFileData() throws Exception {
    return ImmutableList.of(
        transformer(Main.class)
            .transformTypeInsnInMethod(
                "main",
                (int opcode, String type, MethodVisitor visitor) ->
                    visitor.visitTypeInsn(
                        opcode,
                        type.equals(binaryName(Inaccessible.class)) ? "pkg/Inaccessible" : type))
            .replaceClassDescriptorInMethodInstructions(
                descriptor(Inaccessible.class), "Lpkg/Inaccessible;")
            .transform(),
        transformer(Inaccessible.class).setClassDescriptor("Lpkg/Inaccessible;").transform());
  }

  public static class Main {

    public static void main(String[] args) {
      Inaccessible[] array = new Inaccessible[0];
    }
  }

  // TODO(b/320445632): Access modifier should not publicize items with illegal accesses.
  @NoAccessModification
  static class /*pkg.*/ Inaccessible {}
}
