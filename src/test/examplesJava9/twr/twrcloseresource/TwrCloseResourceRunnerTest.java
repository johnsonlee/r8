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
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
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
      StringUtils.lines(
          "A", "E", "G", "H", "I", "J", "K", "iA", "iE", "iG", "iH", "iI", "iJ", "iK", "1", "2",
          "3", "4", "5", "6", "7", "8", "99", "i1", "i2", "i3", "i4", "i5", "i6", "i7", "i8",
          "i99");

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
