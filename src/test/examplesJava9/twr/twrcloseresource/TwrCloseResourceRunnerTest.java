// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package twr.twrcloseresource;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import twr.twrcloseresource.asm.IfaceDump;
import twr.twrcloseresource.asm.TwrCloseResourceTestDump;

@RunWith(Parameterized.class)
public class TwrCloseResourceRunnerTest extends TestBase {

  public static Path ANY_REACHABLE_JAR = Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR, "flow.jar");

  private static final String EXPECTED_RESULT =
      "A\n" + "E\n" + "G\n" + "H\n" + "I\n" + "J\n" + "K\n" + "iA\n" + "iE\n" + "iG\n" + "iH\n"
          + "iI\n" + "iJ\n" + "iK\n" + "1\n" + "2\n" + "3\n" + "4\n" + "5\n" + "6\n" + "7\n" + "8\n"
          + "99\n" + "i1\n" + "i2\n" + "i3\n" + "i4\n" + "i5\n" + "i6\n" + "i7\n" + "i8\n"
          + "i99\n";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeFalse("Missing jar", ToolHelper.isWindows());
    testForD8(parameters.getBackend())
        .addProgramClassFileData(IfaceDump.dump(), TwrCloseResourceTestDump.dump())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .setMinApi(parameters)
        .run(
            parameters.getRuntime(),
            "twr.twrcloseresource.TwrCloseResourceTest",
            ANY_REACHABLE_JAR.toAbsolutePath().toString())
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeFalse("Missing jar", ToolHelper.isWindows());
    parameters.assumeRuntimeTestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(IfaceDump.dump(), TwrCloseResourceTestDump.dump())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .setMinApi(parameters)
        .setMode(CompilationMode.DEBUG)
        .applyIf(
            parameters.isCfRuntime(),
            t ->
                t.addOptionsModification(
                    options ->
                        options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces()))
        .apply(
            b ->
                b.getBuilder()
                    .setDisableTreeShaking(true)
                    .setDisableMinification(true)
                    .addProguardConfiguration(
                        ImmutableList.of("-keepattributes *"), Origin.unknown()))
        .run(
            parameters.getRuntime(),
            "twr.twrcloseresource.TwrCloseResourceTest",
            ANY_REACHABLE_JAR.toAbsolutePath().toString())
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
