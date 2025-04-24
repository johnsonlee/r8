// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk17.records;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedRecordReflectionTest extends TestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("null", "null");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .withPartialCompilation()
        .build();
  }

  @Test
  public void testD8AndJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), UnusedRecordReflection.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .compile()
        .run(parameters.getRuntime(), UnusedRecordReflection.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8TestBuilder<?, ?, ?> builder =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addKeepClassAndMembersRules(UnusedRecordReflectionTest.UnusedRecordReflection.class)
            .addKeepMainRule(UnusedRecordReflection.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), UnusedRecordReflection.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder
        .run(parameters.getRuntime(), UnusedRecordReflection.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class UnusedRecordReflection {

    Record instanceField;

    Record method(int i, Record unused, int j) {
      return null;
    }

    Object reflectiveGetField() {
      try {
        return this.getClass().getDeclaredField("instanceField").get(this);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    Object reflectiveCallMethod() {
      try {
        for (Method declaredMethod : this.getClass().getDeclaredMethods()) {
          if (declaredMethod.getName().equals("method")) {
            return declaredMethod.invoke(this, 0, null, 1);
          }
        }
        throw new RuntimeException("Unreachable");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public static void main(String[] args) {
      System.out.println(new UnusedRecordReflection().reflectiveGetField());
      System.out.println(new UnusedRecordReflection().reflectiveCallMethod());
    }
  }
}
