// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8PartialTestCompileResult;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationPrintConfigurationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build();
  }

  @Test
  public void testBuilder() throws Exception {
    Path out = temp.newFile("config.txt").toPath();
    testForR8Partial(parameters)
        .addR8ExcludedClasses(ExcludedClass.class)
        .addR8IncludedClasses(IncludedClass.class)
        .addKeepClassRules(IncludedClass.class)
        .apply(
            testBuilder ->
                testBuilder
                    .getBuilder()
                    .setProguardConfigurationConsumer(new StringConsumer.FileConsumer(out)))
        .compile();
    inspectFile(out);
  }

  @Test
  public void testKeepRule() throws Exception {
    R8PartialTestCompileResult compileResult =
        testForR8Partial(parameters)
            .addR8ExcludedClasses(ExcludedClass.class)
            .addR8IncludedClasses(IncludedClass.class)
            .addKeepClassRules(IncludedClass.class)
            .addKeepRules("-printconfiguration")
            .collectStdout()
            .compile();
    inspectStdout(compileResult.getStdout());
  }

  @Test
  public void testBoth() throws Exception {
    Path out = temp.newFile("config.txt").toPath();
    R8PartialTestCompileResult compileResult =
        testForR8Partial(parameters)
            .addR8ExcludedClasses(ExcludedClass.class)
            .addR8IncludedClasses(IncludedClass.class)
            .addKeepClassRules(IncludedClass.class)
            .addKeepRules("-printconfiguration")
            .collectStdout()
            .apply(
                testBuilder ->
                    testBuilder
                        .getBuilder()
                        .setProguardConfigurationConsumer(new StringConsumer.FileConsumer(out)))
            .compile();
    inspectFile(out);
    inspectStdout(compileResult.getStdout());
  }

  private void inspectFile(Path out) throws IOException {
    assertTrue(Files.exists(out));
    inspectLines(Files.readAllLines(out));
  }

  private void inspectStdout(String stdout) {
    inspectLines(StringUtils.splitLines(stdout));
  }

  private void inspectLines(List<String> lines) {
    assertEquals(
        1,
        lines.stream()
            .filter(line -> line.equals("-keep class " + IncludedClass.class.getTypeName()))
            .count());
  }

  static class ExcludedClass {}

  static class IncludedClass {}
}
