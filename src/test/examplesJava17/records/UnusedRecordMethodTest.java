// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedRecordMethodTest extends TestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("Hello!");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), UnusedRecordMethod.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), UnusedRecordMethod.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .addKeepRules("-keep class records.UnusedRecordMethodTest$UnusedRecordMethod { *; }")
            .addKeepMainRule(UnusedRecordMethod.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), UnusedRecordMethod.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder
        .run(parameters.getRuntime(), UnusedRecordMethod.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class UnusedRecordMethod {

    Record unusedInstanceMethod(Record unused) {
      return null;
    }

    void printHello() {
      System.out.println("Hello!");
    }

    public static void main(String[] args) {
      new UnusedRecordMethod().printHello();
    }
  }
}
