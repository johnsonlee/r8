// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeInterfaceNoDevirtualizationEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public InvokeInterfaceNoDevirtualizationEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeFalse(enumValueOptimization);
    assumeTrue(enumKeepRules.isNone());
    testForJvm(parameters)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(options -> enableEnumOptions(options, enumValueOptimization))
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private static List<byte[]> getProgramClassFileData() throws IOException {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(MyEnumAccessor.class), "LMyEnumAccessor;")
            .transform(),
        transformer(MyEnum.class).setClassDescriptor("LMyEnum;").transform(),
        transformer(MyEnumAccessor.class)
            .setClassDescriptor("LMyEnumAccessor;")
            .replaceClassDescriptorInMethodInstructions(descriptor(MyEnum.class), "LMyEnum;")
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      greet(MyEnumAccessor.get());
    }

    static void greet(I i) {
      // Cannot be rewritten to call MyEnum#greet since MyEnum is not public and in another package.
      i.greet();
    }
  }

  @NoVerticalClassMerging
  public interface I {

    void greet();
  }

  // Moved to separate package by transformer.
  @NoAccessModification
  enum MyEnum implements I {
    A,
    B;

    @NeverInline
    @Override
    public void greet() {
      System.out.println("Hello, world!");
    }
  }

  // Moved to separate package by transformer.
  public static class MyEnumAccessor {

    public static I get() {
      return System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
    }
  }
}
