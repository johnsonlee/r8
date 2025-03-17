// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingAmbiguousDispatchTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean abstractMethodOnSuperClass;

  @Parameter(2)
  public boolean interfaceAsSymbolicReference;

  @Parameters(name = "{0}, abstractMethodOnSuperClass: {1}, interfaceAsSymbolicReference {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private void setupInput(TestBuilder<?, ?> testBuilder) {
    testBuilder
        .addProgramClasses(Main.class, SuperInterface.class)
        .applyIf(
            abstractMethodOnSuperClass,
            b -> b.addProgramClassFileData(getSuperClassWithFooAsAbstract()),
            b -> b.addProgramClasses(SuperClass.class))
        .applyIf(
            interfaceAsSymbolicReference,
            b -> b.addProgramClassFileData(getProgramClassWithInvokeToInterface()),
            b -> b.addProgramClasses(ProgramClass.class));
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::setupInput)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .apply(this::setupInput)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .apply(this::setupInput)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void checkOutput(TestRunResult<?> result) {
    if (abstractMethodOnSuperClass || interfaceAsSymbolicReference) {
      result.assertFailureWithErrorThatThrows(AbstractMethodError.class);
    } else {
      result.assertSuccessWithOutputLines("SuperClass::foo");
    }
  }

  private byte[] getSuperClassWithFooAsAbstract() throws Exception {
    return transformer(SuperClassAbstract.class)
        .setClassDescriptor(descriptor(SuperClass.class))
        .transform();
  }

  private byte[] getProgramClassWithInvokeToInterface() throws Exception {
    return transformer(ProgramClass.class)
        .transformMethodInsnInMethod(
            "foo",
            (opcode, owner, name, descriptor, isInterface, visitor) ->
                visitor.visitMethodInsn(
                    opcode, binaryName(SuperInterface.class), name, descriptor, true))
        .transform();
  }

  public abstract static class SuperClassAbstract {

    public abstract void foo();
  }

  public abstract static class SuperClass {

    public void foo() {
      System.out.println("SuperClass::foo");
    }
  }

  public interface SuperInterface {

    void foo();
  }

  public static class ProgramClass extends SuperClass implements SuperInterface {

    @Override
    public void foo() {
      super.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ProgramClass().foo();
    }
  }
}
