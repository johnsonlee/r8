// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding.protectedaccess;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.memberrebinding.protectedaccess.p1.ClassWithProtectedField;
import com.android.tools.r8.memberrebinding.protectedaccess.p2.SubClassOfClassWithProtectedField;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingProtectedInProgramHierachyInvalidInputTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(SubClassOfClassWithProtectedField.class, TestClass.class)
        .addProgramClassFileData(getTransformedClass())
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK21),
            r -> r.assertFailureWithErrorThatThrows(VerifyError.class),
            parameters.getCfRuntime().getVm() == CfVm.JDK11,
            SingleTestRunResult::assertFailure, // JDK-11 either crash or report VerifyError.
            r -> r.assertFailureWithOutputThatMatches(containsString("SIGSEGV")));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClasses(SubClassOfClassWithProtectedField.class, TestClass.class)
        .addProgramClassFileData(getTransformedClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        // The code runs fine on Dalvik/ART.
        .assertSuccessWithOutputLines("1");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(SubClassOfClassWithProtectedField.class, TestClass.class)
        .addProgramClassFileData(getTransformedClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addDontObfuscate()
        .run(parameters.getRuntime(), TestClass.class)
        // The code runs fine after R8 processing on both JDK and Dalvik/ART.
        .assertSuccessWithOutputLines("1");
  }

  private byte[] getTransformedClass() throws Exception {
    return transformer(ClassWithProtectedField.class)
        .setAccessFlags(
            ClassWithProtectedField.class.getDeclaredField("f"),
            accessFlags -> {
              accessFlags.unsetPublic();
              accessFlags.setProtected();
            })
        .transform();
  }

  static class TestClass {
    public static void main(String[] args) {
      new SubClassOfClassWithProtectedField(2).m(new ClassWithProtectedField(1));
    }
  }
}
