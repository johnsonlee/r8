// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ConsumerUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultSourceFileWithoutMapOutputTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().withPartialCompilation().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setProguardMapConsumer(null)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .inspectOriginalStackTrace(
            (stackTrace, inspector) -> {
              assertEquals(1, stackTrace.getStackTraceLines().size());
              StackTraceLine stackTraceLine = stackTrace.getStackTraceLines().get(0);
              assertEquals("SourceFile", stackTraceLine.fileName);
            });
  }

  @Test
  public void testCommand() throws Exception {
    Path output = temp.newFile("out.zip").toPath();
    R8.run(
        R8Command.builder()
            .addClassProgramData(ToolHelper.getClassAsBytes(Main.class), Origin.unknown())
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addProguardConfiguration(
                ImmutableList.of("-keep class * { <methods>; }"), Origin.unknown())
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setOutput(output, OutputMode.DexIndexed)
            .build());
    ProcessResult artResult =
        runOnArtRaw(
            AndroidApp.builder().addProgramFile(output).build(),
            Main.class.getTypeName(),
            ConsumerUtils.emptyConsumer(),
            parameters.getRuntime().asDex().getVm());
    assertNotEquals(0, artResult.exitCode);
    assertThat(
        artResult.stderr, allOf(containsString("SourceFile"), not(containsString("r8-map-id"))));
  }

  static class Main {

    public static void main(String[] args) {
      throw new RuntimeException();
    }
  }
}
