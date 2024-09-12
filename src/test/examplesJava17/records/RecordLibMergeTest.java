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

@RunWith(Parameterized.class)
public class RecordLibMergeTest extends TestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("true", "true");

  private final TestParameters parameters;

  public RecordLibMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testR8Merge() throws Exception {
    parameters.assumeR8TestParameters();
    Path lib =
        testForR8(Backend.CF)
            .addProgramClassesAndInnerClasses(RecordLib.class)
            .addKeepRules(
                "-keep class records.RecordLib { public static java.lang.Object getRecord(); }")
            .addKeepRules("-keep class records.RecordLib$LibRecord")
            .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
            .compile()
            .writeToZip();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramFiles(lib)
            .addProgramClassesAndInnerClasses(RecordMain.class)
            .setMinApi(parameters)
            .addKeepMainRule(RecordMain.class)
            .addKeepRules("-keep class records.RecordLib$LibRecord")
            .addKeepRules("-keep class records.RecordMain$MainRecord");
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), RecordMain.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder.run(parameters.getRuntime(), RecordMain.class).assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
