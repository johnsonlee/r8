// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmptyRecordTest extends TestBase {

  private static final String EXPECTED_RESULT_D8 = StringUtils.lines("Empty[]");
  private static final String EXPECTED_RESULT_R8_MINIFICATION = StringUtils.lines("a[]");
  private static final String EXPECTED_RESULT_R8_NO_MINIFICATION =
      StringUtils.lines("EmptyRecordTest$Empty[]");

  @Parameter(0)
  public boolean enableMinification;

  @Parameter(1)
  public boolean enableRepackaging;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, minification: {0}, repackage: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
            .withDexRuntimes()
            .withAllApiLevelsAlsoForCf()
            .build());
  }

  @Test
  public void testJvm() throws Exception {
    assumeFalse("Only applicable for R8", enableMinification);
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testD8() throws Exception {
    assumeFalse("Only applicable for R8", enableMinification || enableRepackaging);
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeDexRuntime();
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(TestClass.class)
        .applyIf(
            parameters.isCfRuntime(),
            testBuilder -> testBuilder.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .addDontObfuscateUnless(enableMinification)
        .applyIf(enableRepackaging, b -> b.addKeepRules("-repackageclasses p"))
        .setMinApi(parameters)
        .compile()
        .applyIf(
            parameters.isCfRuntime(),
            compileResult -> compileResult.inspect(RecordTestUtils::assertRecordsAreRecords))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(
            enableMinification
                ? EXPECTED_RESULT_R8_MINIFICATION
                : EXPECTED_RESULT_R8_NO_MINIFICATION);
  }

  record Empty() {}

  public class TestClass {

    public static void main(String[] args) {
      System.out.println(new Empty());
    }
  }
}
