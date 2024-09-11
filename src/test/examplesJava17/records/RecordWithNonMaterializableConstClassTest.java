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
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import records.differentpackage.PrivateConstClass;

@RunWith(Parameterized.class)
public class RecordWithNonMaterializableConstClassTest extends TestBase {

  private static final String PRIVATE_CLASS_NAME =
      "records.differentpackage.PrivateConstClass$PrivateClass";
  private static final Class<?> EXTRA_DATA = PrivateConstClass.class;
  private static final String EXPECTED_RESULT_FORMAT =
      StringUtils.lines("%s[%s=class " + PRIVATE_CLASS_NAME + "]");
  private static final String EXPECTED_RESULT_D8 =
      String.format(EXPECTED_RESULT_FORMAT, "MyRecordWithConstClass", "theClass");
  private static final String EXPECTED_RESULT_R8 = String.format(EXPECTED_RESULT_FORMAT, "a", "a");

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
        .addProgramClasses(EXTRA_DATA)
        .addInnerClasses(EXTRA_DATA)
        .run(parameters.getRuntime(), RecordWithConstClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .addProgramClasses(EXTRA_DATA)
        .addInnerClasses(EXTRA_DATA)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), RecordWithConstClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_D8);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .addProgramClasses(EXTRA_DATA)
        .addInnerClasses(EXTRA_DATA)
        .apply(this::configureR8)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), RecordWithConstClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  @Test
  public void testR8CfThenRecompile() throws Exception {
    parameters.assumeR8TestParameters();
    Path desugared =
        testForR8(Backend.CF)
            .addInnerClassesAndStrippedOuter(getClass())
            .addProgramClasses(EXTRA_DATA)
            .addInnerClasses(EXTRA_DATA)
            .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
            .apply(this::configureR8)
            .compile()
            .writeToZip();
    // TODO(b/288360309): Correctly deal with non-identity lenses in R8 record rewriting.
    parameters.assumeDexRuntime();
    testForR8(parameters.getBackend())
        .addProgramFiles(desugared)
        .apply(this::configureR8)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), RecordWithConstClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT_R8);
  }

  private void configureR8(R8FullTestBuilder testBuilder) {
    testBuilder
        .addKeepMainRule(RecordWithConstClass.class)
        .addKeepRules("-keep class " + PRIVATE_CLASS_NAME)
        .applyIf(
            parameters.isCfRuntime(),
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()));
  }

  record MyRecordWithConstClass(Class<?> theClass) {}

  public static class RecordWithConstClass {

    public static void main(String[] args) {
      MyRecordWithConstClass inst =
          new MyRecordWithConstClass(PrivateConstClass.getPrivateConstClass());
      System.out.println(inst);
    }
  }
}
